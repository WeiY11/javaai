package com.example.javaai.document;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.javaai.model.dto.ApiResponse;
import com.example.javaai.model.entity.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

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
        return ResponseEntity.ok(ApiResponse.success(documentService.listByKnowledgeBase(knowledgeBaseId, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Document>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(documentService.getById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<Void>> retryIngestion(@PathVariable Long id) {
        documentService.retryIngestion(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
