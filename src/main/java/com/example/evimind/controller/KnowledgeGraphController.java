package com.example.evimind.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.service.KnowledgeGraphService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{kbId}/graph")
@RequiredArgsConstructor
public class KnowledgeGraphController {

  private final KnowledgeGraphService knowledgeGraphService;

  /** 获取知识库的完整图谱数据（实体 + 关系 + 统计） */
  @GetMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> getGraph(@PathVariable Long kbId) {
    Map<String, Object> graph = knowledgeGraphService.getGraph(kbId);
    return ResponseEntity.ok(ApiResponse.success(graph));
  }

  /** 获取知识库的图谱统计信息 */
  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(@PathVariable Long kbId) {
    Map<String, Object> stats = knowledgeGraphService.getStats(kbId);
    return ResponseEntity.ok(ApiResponse.success(stats));
  }

  /** 获取指定实体的邻居节点和关系 */
  @GetMapping("/entities/{entityId}/neighbors")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getNeighbors(
      @PathVariable Long entityId) {
    Map<String, Object> neighbors = knowledgeGraphService.getNeighbors(entityId);
    return ResponseEntity.ok(ApiResponse.success(neighbors));
  }

  /** 多跳路径搜索：从 source 到 target 的最短路径 */
  @GetMapping("/path")
  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> findPath(
      @PathVariable Long kbId,
      @RequestParam Long source,
      @RequestParam Long target,
      @RequestParam(defaultValue = "3") int maxHops) {
    List<Map<String, Object>> path = knowledgeGraphService.findPath(source, target, maxHops);
    return ResponseEntity.ok(ApiResponse.success(path));
  }
}
