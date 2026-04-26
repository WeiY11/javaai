package com.example.javaai.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.javaai.ingestion.EtlPipeline;
import com.example.javaai.identity.GroupContext;
import com.example.javaai.mapper.DocumentMapper;
import com.example.javaai.model.dto.ApiResponse;
import com.example.javaai.model.entity.Document;
import com.example.javaai.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final MinioStorageService minioStorageService;
    private final EtlPipeline etlPipeline;

    private static final Set<String> ALLOWED_FORMATS = Set.of(
            "pdf", "xlsx", "xls", "docx", "doc", "csv", "json", "md", "txt",
            "py", "java", "sql", "xml", "yaml", "yml", "log", "tex", "markdown"
    );
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    public Document upload(MultipartFile file, Long knowledgeBaseId) {
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
            minioStorageService.uploadFile(objectName, is, file.getSize(), file.getContentType());
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

        triggerIngestionAsync(doc.getId());
        return doc;
    }

    @Async
    public void triggerIngestionAsync(Long documentId) {
        try {
            etlPipeline.processDocument(documentId);
        } catch (Exception e) {
            log.error("Async ingestion failed for document {}", documentId, e);
        }
    }

    public Page<Document> listByKnowledgeBase(Long kbId, int page, int size) {
        return documentMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKnowledgeBaseId, kbId)
                        .orderByDesc(Document::getCreatedAt)
        );
    }

    public Document getById(Long id) {
        return documentMapper.selectById(id);
    }

    public void delete(Long id) {
        etlPipeline.deleteDocument(id);
    }

    public void retryIngestion(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
        doc.setIngestionStatus("PENDING");
        documentMapper.updateById(doc);
        triggerIngestionAsync(id);
    }

    private String getFormat(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
