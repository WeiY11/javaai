package com.example.evimind.document;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.evimind.audit.Auditable;
import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.DocumentChunk;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;
  private final DocumentChunkMapper documentChunkMapper;

  @Auditable(action = "DOCUMENT_UPLOAD", resourceType = "DOCUMENT")
  @PostMapping("/upload")
  public ResponseEntity<ApiResponse<Document>> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
    return ResponseEntity.ok(ApiResponse.success(documentService.upload(file, knowledgeBaseId)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<Page<Document>>> list(
      @RequestParam Long knowledgeBaseId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ResponseEntity.ok(
        ApiResponse.success(documentService.listByKnowledgeBase(knowledgeBaseId, page, size)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<Document>> get(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(documentService.getById(id)));
  }

  @GetMapping("/{id}/chunks")
  public ResponseEntity<ApiResponse<List<DocumentChunk>>> getChunks(@PathVariable Long id) {
    Document doc = documentService.getById(id);
    List<DocumentChunk> chunks =
        documentChunkMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, id)
                .orderByAsc(DocumentChunk::getChunkIndex));
    return ResponseEntity.ok(ApiResponse.success(chunks));
  }

  @Auditable(action = "DOCUMENT_DELETE", resourceType = "DOCUMENT", resourceIdExpression = "#id")
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
    documentService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @Auditable(action = "DOCUMENT_RETRY", resourceType = "DOCUMENT", resourceIdExpression = "#id")
  @PostMapping("/{id}/retry")
  public ResponseEntity<ApiResponse<Void>> retryIngestion(@PathVariable Long id) {
    documentService.retryIngestion(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @PostMapping("/batch-delete")
  public ResponseEntity<ApiResponse<Void>> batchDelete(@RequestBody List<Long> ids) {
    ids.forEach(documentService::delete);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @PostMapping("/batch-reingest")
  public ResponseEntity<ApiResponse<Void>> batchReingest(@RequestBody List<Long> ids) {
    ids.forEach(documentService::retryIngestion);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
