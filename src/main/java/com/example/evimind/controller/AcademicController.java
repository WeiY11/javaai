package com.example.evimind.controller;

import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.model.entity.CitationLink;
import com.example.evimind.service.CitationNetworkService;
import com.example.evimind.service.LiteratureReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/academic")
@RequiredArgsConstructor
public class AcademicController {

    private final CitationNetworkService citationNetworkService;
    private final LiteratureReviewService literatureReviewService;

    /**
     * 获取知识库的引用网络图数据（节点 + 边）
     */
    @GetMapping("/knowledge-bases/{kbId}/citation-graph")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCitationGraph(
            @PathVariable Long kbId) {
        Map<String, Object> graph = citationNetworkService.getCitationGraph(kbId);
        return ResponseEntity.ok(ApiResponse.success(graph));
    }

    /**
     * 获取知识库的引用统计信息
     */
    @GetMapping("/knowledge-bases/{kbId}/citation-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCitationStats(
            @PathVariable Long kbId) {
        Map<String, Object> stats = citationNetworkService.getCitationStats(kbId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 获取指定文档的引用列表
     */
    @GetMapping("/documents/{docId}/citations")
    public ResponseEntity<ApiResponse<List<CitationLink>>> getDocumentCitations(
            @PathVariable Long docId) {
        List<CitationLink> citations = citationNetworkService.getCitationsForDocument(docId);
        return ResponseEntity.ok(ApiResponse.success(citations));
    }

    /**
     * 获取与指定文档共被引的文档列表
     */
    @GetMapping("/documents/{docId}/co-cited")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCoCitedDocuments(
            @PathVariable Long docId) {
        List<Map<String, Object>> coCited = citationNetworkService.getCoCitedDocuments(docId);
        return ResponseEntity.ok(ApiResponse.success(coCited));
    }

    /**
     * 基于知识库生成指定主题的文献综述
     */
    @PostMapping("/knowledge-bases/{kbId}/literature-review")
    public ResponseEntity<ApiResponse<String>> generateLiteratureReview(
            @PathVariable Long kbId,
            @RequestBody Map<String, String> body) {
        String topic = body.get("topic");
        if (topic == null || topic.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "topic is required"));
        }
        String review = literatureReviewService.generateReview(kbId, topic);
        return ResponseEntity.ok(ApiResponse.success(review));
    }
}
