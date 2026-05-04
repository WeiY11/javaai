package com.example.javaai.assistant;

import com.example.javaai.model.dto.ApiResponse;
import com.example.javaai.model.dto.MessageRequest;
import com.example.javaai.model.entity.Conversation;
import com.example.javaai.model.entity.Message;
import com.example.javaai.service.ConversationExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationExportService conversationExportService;

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

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamMessage(@PathVariable Long id, @RequestBody MessageRequest request) {
        return conversationService.streamMessage(id, request.getContent(),
                request.getTemperature(), request.getTopP(), request.getMaxTokens());
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<ApiResponse<Conversation>> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.renameConversation(id, body.get("title"))));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<ApiResponse<String>> export(
            @PathVariable Long id,
            @RequestParam(defaultValue = "markdown") String format) {
        String result;
        if ("json".equalsIgnoreCase(format)) {
            result = conversationExportService.exportAsJson(id);
        } else {
            result = conversationExportService.exportAsMarkdown(id);
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
