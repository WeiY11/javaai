package com.example.javaai.assistant;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.javaai.model.dto.ApiResponse;
import com.example.javaai.model.entity.Conversation;
import com.example.javaai.model.entity.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ApiResponse<Conversation>> create(
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(defaultValue = "deepseek") String modelProvider) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.createConversation(knowledgeBaseId, modelProvider)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Conversation>>> list() {
        return ResponseEntity.ok(ApiResponse.success(conversationService.listConversations()));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<List<Message>>> getMessages(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(conversationService.getHistory(id)));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<Message>> addMessage(
            @PathVariable Long id,
            @RequestParam String role,
            @RequestParam String content,
            @RequestParam(required = false) String citations,
            @RequestParam(required = false) String toolCalls) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.addMessage(id, role, content, citations, toolCalls)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
