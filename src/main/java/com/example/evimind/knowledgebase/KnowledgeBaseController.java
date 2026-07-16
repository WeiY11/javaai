package com.example.evimind.knowledgebase;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.model.entity.KbMember;
import com.example.evimind.model.entity.KnowledgeBase;
import com.example.evimind.retrieval.SearchResult;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

  private final KnowledgeBaseService knowledgeBaseService;

  @PostMapping
  public ResponseEntity<ApiResponse<KnowledgeBase>> create(@RequestBody KnowledgeBase kb) {
    return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.create(kb)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<KnowledgeBase>> update(
      @PathVariable Long id, @RequestBody KnowledgeBase kb) {
    kb.setId(id);
    return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.update(kb)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
    knowledgeBaseService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<Page<KnowledgeBase>>> list(
      @RequestParam(required = false) Long groupId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.listAccessible(page, size)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<KnowledgeBase>> get(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.getById(id)));
  }

  @PostMapping("/{id}/search")
  public ResponseEntity<ApiResponse<List<SearchResult>>> search(
      @PathVariable Long id, @RequestBody KnowledgeBaseSearchRequest request) {
    return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.search(id, request)));
  }

  @PostMapping("/{id}/members")
  public ResponseEntity<ApiResponse<KbMember>> addMember(
      @PathVariable Long id,
      @RequestParam Long userId,
      @RequestParam(defaultValue = "MEMBER") String role) {
    return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.addMember(id, userId, role)));
  }

  @DeleteMapping("/{id}/members/{userId}")
  public ResponseEntity<ApiResponse<Void>> removeMember(
      @PathVariable Long id, @PathVariable Long userId) {
    knowledgeBaseService.removeMember(id, userId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @GetMapping("/{id}/members")
  public ResponseEntity<ApiResponse<java.util.List<KbMember>>> listMembers(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.listMembers(id)));
  }
}
