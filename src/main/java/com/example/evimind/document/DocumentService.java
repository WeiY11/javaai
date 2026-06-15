package com.example.evimind.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.ingestion.EtlPipeline;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.KbMemberMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.KbMember;
import com.example.evimind.storage.LocalFileStorageService;
import com.example.evimind.storage.MinioStorageService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DocumentService {

  @Autowired private DocumentMapper documentMapper;
  @Autowired private KnowledgeBaseMapper knowledgeBaseMapper;
  @Autowired private KbMemberMapper kbMemberMapper;

  @Autowired(required = false)
  private MinioStorageService minioStorageService;

  @Autowired(required = false)
  private LocalFileStorageService localFileStorageService;

  @Autowired private EtlPipeline etlPipeline;

  @Autowired(required = false)
  private com.example.evimind.service.DocumentPermissionService documentPermissionService;

  private static final Set<String> ALLOWED_FORMATS =
      Set.of(
          "pdf",
          "xlsx",
          "xls",
          "docx",
          "doc",
          "csv",
          "json",
          "md",
          "txt",
          "py",
          "java",
          "sql",
          "xml",
          "yaml",
          "yml",
          "log",
          "tex",
          "markdown",
          "pptx",
          "epub");
  private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

  public Document upload(MultipartFile file, Long knowledgeBaseId) {
    requireKbMember(knowledgeBaseId);

    if (minioStorageService == null && localFileStorageService == null) {
      throw new RuntimeException("No storage service available");
    }
    String originalName = file.getOriginalFilename();
    String format = getFormat(originalName);

    if (!ALLOWED_FORMATS.contains(format.toLowerCase())) {
      throw new IllegalArgumentException("Unsupported file format: " + format);
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new IllegalArgumentException("File size exceeds 50MB limit");
    }

    String objectName = knowledgeBaseId + "/" + System.currentTimeMillis() + "_" + originalName;
    try (InputStream is = file.getInputStream()) {
      if (minioStorageService != null) {
        minioStorageService.uploadFile(objectName, is, file.getSize(), file.getContentType());
      } else {
        localFileStorageService.uploadFile(objectName, is, file.getSize(), file.getContentType());
      }
    } catch (IOException e) {
      throw new RuntimeException("File upload failed", e);
    }

    Document doc = new Document();
    doc.setKnowledgeBaseId(knowledgeBaseId);
    doc.setFileName(originalName);
    doc.setFileFormat(format);
    doc.setFileSize(file.getSize());
    doc.setStoragePath(objectName);
    doc.setIngestionStatus("PENDING");
    doc.setChunkCount(0);
    doc.setUploaderId(GroupContext.getUserId());
    documentMapper.insert(doc);

    // Grant ADMIN permission to the uploader
    if (documentPermissionService != null) {
      try {
        documentPermissionService.grantOwnerPermission(doc.getId());
      } catch (Exception e) {
        log.warn(
            "Failed to grant owner permission for document {}: {}", doc.getId(), e.getMessage());
      }
    }

    triggerIngestionAsync(doc.getId());
    return doc;
  }

  @Async("analysisTaskExecutor")
  public void triggerIngestionAsync(Long documentId) {
    try {
      etlPipeline.processDocument(documentId);
    } catch (Exception e) {
      log.error("Async ingestion failed for document {}", documentId, e);
    }
  }

  public Page<Document> listByKnowledgeBase(Long kbId, int page, int size) {
    requireKbMember(kbId);
    return documentMapper.selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<Document>()
            .eq(Document::getKnowledgeBaseId, kbId)
            .orderByDesc(Document::getCreatedAt));
  }

  public Document getById(Long id) {
    Document doc = documentMapper.selectById(id);
    if (doc != null) {
      requireKbMember(doc.getKnowledgeBaseId());
    }
    return doc;
  }

  public void delete(Long id) {
    Document doc = documentMapper.selectById(id);
    if (doc != null) {
      requireKbMember(doc.getKnowledgeBaseId());
    }
    etlPipeline.deleteDocument(id);
  }

  public void retryIngestion(Long id) {
    Document doc = documentMapper.selectById(id);
    if (doc == null) {
      throw new IllegalArgumentException("Document not found: " + id);
    }
    requireKbMember(doc.getKnowledgeBaseId());
    doc.setIngestionStatus("PENDING");
    documentMapper.updateById(doc);
    triggerIngestionAsync(id);
  }

  private void requireKbMember(Long knowledgeBaseId) {
    Long userId = GroupContext.getUserId();
    if (userId == null) return;
    Long count =
        kbMemberMapper.selectCount(
            new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KbMember::getUserId, userId));
    if (count == 0) {
      throw new SecurityException(
          "Access denied: you are not a member of knowledge base " + knowledgeBaseId);
    }
  }

  private String getFormat(String filename) {
    if (filename == null) return "";
    int dot = filename.lastIndexOf('.');
    return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
  }
}
