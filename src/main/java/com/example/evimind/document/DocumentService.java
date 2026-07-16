package com.example.evimind.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
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

  @Autowired private com.example.evimind.service.DocumentPermissionService documentPermissionService;

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
  private static final int MAX_RETRY_COUNT = 3;
  private static final Set<String> RUNNING_STAGES =
      Set.of("EXTRACTING", "CLEANING", "CHUNKING", "PERSISTING", "EMBEDDING", "INDEXING", "ENRICHING");
  private static final Map<String, Set<String>> ALLOWED_MIME_TYPES =
      Map.ofEntries(
          Map.entry("pdf", Set.of("application/pdf")),
          Map.entry("txt", Set.of("text/plain", "application/octet-stream")),
          Map.entry("md", Set.of("text/markdown", "text/plain", "application/octet-stream")),
          Map.entry("markdown", Set.of("text/markdown", "text/plain", "application/octet-stream")),
          Map.entry("csv", Set.of("text/csv", "application/vnd.ms-excel", "text/plain")),
          Map.entry("json", Set.of("application/json", "text/plain")),
          Map.entry("doc", Set.of("application/msword", "application/octet-stream")),
          Map.entry(
              "docx",
              Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
          Map.entry("xls", Set.of("application/vnd.ms-excel", "application/octet-stream")),
          Map.entry(
              "xlsx",
              Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
          Map.entry(
              "pptx",
              Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
          Map.entry("epub", Set.of("application/epub+zip", "application/zip")),
          Map.entry("py", Set.of("text/x-python", "text/plain", "application/octet-stream")),
          Map.entry("java", Set.of("text/x-java-source", "text/plain", "application/octet-stream")),
          Map.entry("sql", Set.of("application/sql", "text/plain", "application/octet-stream")),
          Map.entry("xml", Set.of("application/xml", "text/xml", "text/plain")),
          Map.entry("yaml", Set.of("application/x-yaml", "text/yaml", "text/plain")),
          Map.entry("yml", Set.of("application/x-yaml", "text/yaml", "text/plain")),
          Map.entry("log", Set.of("text/plain", "application/octet-stream")),
          Map.entry("tex", Set.of("text/x-tex", "text/plain", "application/octet-stream")));

  public Document upload(MultipartFile file, Long knowledgeBaseId) {
    requireKbMember(knowledgeBaseId);

    if (minioStorageService == null && localFileStorageService == null) {
      throw new RuntimeException("No storage service available");
    }
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("File must not be empty");
    }
    String originalName = sanitizeOriginalFilename(file.getOriginalFilename());
    String format = getFormat(originalName);

    if (!ALLOWED_FORMATS.contains(format.toLowerCase())) {
      throw new IllegalArgumentException("Unsupported file format: " + format);
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new IllegalArgumentException("File size exceeds 50MB limit");
    }
    validateContentType(format, file.getContentType());

    String fileId = UUID.randomUUID().toString();
    String objectName = knowledgeBaseId + "/" + fileId + "/" + fileId + "." + format;
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
    doc.setIngestionStatus("UPLOADED");
    doc.setRetryCount(0);
    doc.setIngestionVersion(0);
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

  @Async("ingestionTaskExecutor")
  public void triggerIngestionAsync(Long documentId) {
    try {
      etlPipeline.processDocument(documentId);
    } catch (Exception e) {
      log.error("Async ingestion failed for document {}", documentId, e);
    }
  }

  public Page<Document> listByKnowledgeBase(Long kbId, int page, int size) {
    requireKbMember(kbId);
    Page<Document> documentPage = new Page<>(page, size);
    if (GroupContext.isAdmin()) {
      return documentMapper.selectPage(
          documentPage,
          new LambdaQueryWrapper<Document>()
              .eq(Document::getKnowledgeBaseId, kbId)
              .orderByDesc(Document::getCreatedAt));
    }
    documentMapper.selectAccessiblePage(documentPage, kbId, GroupContext.getUserId());
    return documentPage;
  }

  public Document getById(Long id) {
    Document doc = documentMapper.selectById(id);
    if (doc != null) {
      requireDocumentPermission(doc, com.example.evimind.service.DocumentPermissionService.PERM_READ);
    }
    return doc;
  }

  public void delete(Long id) {
    Document doc = documentMapper.selectById(id);
    if (doc == null) {
      throw new IllegalArgumentException("Document not found: " + id);
    }
    requireDocumentPermission(doc, com.example.evimind.service.DocumentPermissionService.PERM_ADMIN);
    etlPipeline.deleteDocument(id);
  }

  public void retryIngestion(Long id) {
    Document doc = documentMapper.selectById(id);
    if (doc == null) {
      throw new IllegalArgumentException("Document not found: " + id);
    }
    requireDocumentPermission(doc, com.example.evimind.service.DocumentPermissionService.PERM_WRITE);
    if (RUNNING_STAGES.contains(doc.getIngestionStatus())) {
      throw new IllegalStateException("Document ingestion is already running");
    }
    int retryCount = doc.getRetryCount() != null ? doc.getRetryCount() : 0;
    if (retryCount >= MAX_RETRY_COUNT) {
      throw new IllegalStateException("Document ingestion retry limit exceeded");
    }
    doc.setRetryCount(retryCount + 1);
    doc.setIngestionStatus("UPLOADED");
    documentMapper.updateById(doc);
    triggerIngestionAsync(id);
  }

  private void requireKbMember(Long knowledgeBaseId) {
    Long userId = GroupContext.getUserId();
    if (userId == null) {
      throw new AuthenticationCredentialsNotFoundException("Not authenticated");
    }
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

  private void requireDocumentPermission(
      Document document, String requiredPermission) {
    requireKbMember(document.getKnowledgeBaseId());
    if (!GroupContext.isAdmin() && documentPermissionService.hasRestrictions(document.getId())) {
      documentPermissionService.requirePermission(
          document.getId(), GroupContext.getUserId(), requiredPermission);
    }
  }

  private String sanitizeOriginalFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new IllegalArgumentException("File name must not be empty");
    }
    String normalized = originalFilename.replace('\\', '/');
    String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    if (name.isBlank() || name.equals(".") || name.equals("..") || name.contains("..")) {
      throw new IllegalArgumentException("Invalid file name");
    }
    return name;
  }

  private void validateContentType(String format, String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return;
    }
    String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    Set<String> allowed = ALLOWED_MIME_TYPES.get(format.toLowerCase(Locale.ROOT));
    if (allowed != null && !allowed.contains(normalized)) {
      throw new IllegalArgumentException(
          "File extension does not match MIME type: " + format + " / " + contentType);
    }
  }
}
