package com.example.evimind.assistant;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.model.dto.MessageRequest;
import com.example.evimind.model.entity.Conversation;
import com.example.evimind.model.entity.Message;
import com.example.evimind.service.ConversationExportService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

  private final ConversationService conversationService;
  private final ConversationExportService conversationExportService;

  @PostMapping
  public ResponseEntity<ApiResponse<Conversation>> create(
      @RequestParam(required = false) Long knowledgeBaseId,
      @RequestParam(required = false) String modelProvider,
      @RequestBody(required = false) Map<String, Object> body) {

    Long kbId = knowledgeBaseId;
    String provider = modelProvider;

    if (body != null) {
      if (body.get("knowledgeBaseId") != null) {
        kbId = Long.valueOf(body.get("knowledgeBaseId").toString());
      }
      if (body.get("modelProvider") != null) {
        provider = body.get("modelProvider").toString();
      }
    }

    return ResponseEntity.ok(
        ApiResponse.success(conversationService.createConversation(kbId, provider)));
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
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String content,
      @RequestParam(required = false) String citations,
      @RequestParam(required = false) String toolCalls,
      @RequestBody(required = false) Map<String, Object> body) {

    String r = role;
    String c = content;
    String cit = citations;
    String tc = toolCalls;

    if (body != null) {
      if (body.get("role") != null) r = body.get("role").toString();
      if (body.get("content") != null) c = body.get("content").toString();
      if (body.get("citations") != null) cit = body.get("citations").toString();
      if (body.get("toolCalls") != null) tc = body.get("toolCalls").toString();
    }

    return ResponseEntity.ok(
        ApiResponse.success(conversationService.addMessage(id, r, c, cit, tc)));
  }

  @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> streamMessage(@PathVariable Long id, @RequestBody MessageRequest request) {
    return conversationService.streamMessage(
        id,
        request.getContent(),
        request.getTemperature(),
        request.getTopP(),
        request.getMaxTokens(),
        request.getModelName(),
        request.getThinking(),
        request.getReasoningEffort());
  }

  @PutMapping("/{id}/rename")
  public ResponseEntity<ApiResponse<Conversation>> rename(
      @PathVariable Long id, @RequestBody Map<String, String> body) {
    return ResponseEntity.ok(
        ApiResponse.success(conversationService.renameConversation(id, body.get("title"))));
  }

  @GetMapping("/{id}/export")
  public ResponseEntity<ApiResponse<String>> export(
      @PathVariable Long id, @RequestParam(defaultValue = "markdown") String format) {
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
