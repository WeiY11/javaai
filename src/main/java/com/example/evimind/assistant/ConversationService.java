package com.example.evimind.assistant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.config.PromptTemplateManager;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.ConversationMapper;
import com.example.evimind.mapper.MessageMapper;
import com.example.evimind.model.dto.StreamEvent;
import com.example.evimind.model.entity.Conversation;
import com.example.evimind.model.entity.Message;
import com.example.evimind.qa.RagPipeline;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class ConversationService {

  private final ConversationMapper conversationMapper;
  private final MessageMapper messageMapper;
  private final PromptTemplateManager promptTemplateManager;
  private final RagPipeline ragPipeline;
  private final Map<String, ChatClient> chatClients;

  public ConversationService(
      ConversationMapper conversationMapper,
      MessageMapper messageMapper,
      PromptTemplateManager promptTemplateManager,
      RagPipeline ragPipeline,
      Map<String, ChatClient> chatClients) {
    this.conversationMapper = conversationMapper;
    this.messageMapper = messageMapper;
    this.promptTemplateManager = promptTemplateManager;
    this.ragPipeline = ragPipeline;
    this.chatClients = chatClients;
  }

  private static final int SUMMARY_WINDOW = 10;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional
  public Conversation createConversation(Long knowledgeBaseId, String modelProvider) {
    Conversation conv = new Conversation();
    conv.setUserId(GroupContext.getUserId());
    conv.setKnowledgeBaseId(knowledgeBaseId);
    conv.setModelProvider(modelProvider);
    conv.setStatus("ACTIVE");
    conversationMapper.insert(conv);
    return conv;
  }

  @Transactional
  public Message addMessage(
      Long conversationId, String role, String content, String citations, String toolCalls) {
    requireConversationOwner(conversationId);
    Message msg = new Message();
    msg.setConversationId(conversationId);
    msg.setRole(role);
    msg.setContent(content);
    msg.setCitations(citations);
    msg.setToolCalls(toolCalls);
    messageMapper.insert(msg);

    checkAndGenerateSummary(conversationId);
    return msg;
  }

  public Flux<String> streamMessage(Long conversationId, String content) {
    return streamMessage(conversationId, content, null, null, null, null, null, null);
  }

  public Flux<String> streamMessage(
      Long conversationId,
      String content,
      Double temperature,
      Double topP,
      Integer maxTokens,
      String modelName,
      Boolean thinking,
      String reasoningEffort) {
    Conversation conv = requireConversationOwner(conversationId);

    if (conv.getKnowledgeBaseId() == null) {
      return Flux.just(StreamEvent.error("请先选择知识库再发起对话"));
    }

    if (!ragPipeline.isKbMember(conv.getKnowledgeBaseId())) {
      return Flux.just(
          StreamEvent.error("Access denied: you are not a member of this knowledge base"));
    }

    Message userMsg = new Message();
    userMsg.setConversationId(conversationId);
    userMsg.setRole("user");
    userMsg.setContent(content);
    messageMapper.insert(userMsg);

    String modelProvider = conv.getModelProvider() != null ? conv.getModelProvider() : "deepseek";

    // 构建对话历史字符串，用于 Query Rewrite（核心改写能力）
    String conversationHistory = buildConversationHistory(conversationId);

    StringBuilder fullContent = new StringBuilder();
    String[] citationsHolder = new String[1];

    return ragPipeline
        .streamQuery(
            content,
            conv.getKnowledgeBaseId(),
            modelProvider,
            temperature,
            topP,
            maxTokens,
            modelName,
            thinking,
            reasoningEffort,
            conversationHistory)
        .map(
            event -> {
              try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(event, Map.class);
                String type = (String) map.get("type");
                if ("token".equals(type)) {
                  String text = (String) map.get("text");
                  if (text != null) fullContent.append(text);
                } else if ("citations".equals(type)) {
                  citationsHolder[0] = objectMapper.writeValueAsString(map.get("citations"));
                } else if ("done".equals(type)) {
                  saveAssistantMessage(conversationId, fullContent.toString(), citationsHolder[0]);
                  return "{\"type\":\"done\",\"messageId\":" + map.get("messageId") + "}";
                }
              } catch (JsonProcessingException e) {
                log.warn("Failed to parse stream event", e);
              }
              return event;
            })
        .doOnComplete(
            () -> {
              if (fullContent.length() > 0) {
                saveAssistantMessage(conversationId, fullContent.toString(), citationsHolder[0]);
              }
              checkAndGenerateSummary(conversationId);
            })
        .doOnError(e -> log.error("Stream error for conversation {}", conversationId, e));
  }

  private void saveAssistantMessage(Long conversationId, String content, String citations) {
    if (content == null || content.isBlank()) return;
    Message msg = new Message();
    msg.setConversationId(conversationId);
    msg.setRole("assistant");
    msg.setContent(content);
    msg.setCitations(citations);
    messageMapper.insert(msg);
  }

  public List<Message> getHistory(Long conversationId) {
    requireConversationOwner(conversationId);
    return messageMapper.selectList(
        new LambdaQueryWrapper<Message>()
            .eq(Message::getConversationId, conversationId)
            .orderByAsc(Message::getCreatedAt));
  }

  public List<Conversation> listConversations() {
    Long userId = GroupContext.getUserId();
    return conversationMapper.selectList(
        new LambdaQueryWrapper<Conversation>()
            .eq(Conversation::getUserId, userId)
            .eq(Conversation::getStatus, "ACTIVE")
            .orderByDesc(Conversation::getUpdatedAt));
  }

  @Transactional
  public void deleteConversation(Long conversationId) {
    requireConversationOwner(conversationId);
    Conversation conv = conversationMapper.selectById(conversationId);
    if (conv != null) {
      conv.setStatus("DELETED");
      conversationMapper.updateById(conv);
    }
  }

  private Conversation requireConversationOwner(Long conversationId) {
    Conversation conv = conversationMapper.selectById(conversationId);
    if (conv == null) {
      throw new IllegalArgumentException("Conversation not found: " + conversationId);
    }
    Long userId = GroupContext.getUserId();
    if (userId != null && !userId.equals(conv.getUserId())) {
      throw new SecurityException("Access denied: you do not own this conversation");
    }
    return conv;
  }

  public String buildContext(Long conversationId, String currentQuery) {
    Conversation conv = conversationMapper.selectById(conversationId);
    if (conv == null) return currentQuery;

    StringBuilder context = new StringBuilder();

    if (conv.getSummary() != null && !conv.getSummary().isBlank()) {
      context.append("[会话摘要]\n").append(conv.getSummary()).append("\n\n");
    }

    List<Message> recentMessages =
        messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getCreatedAt)
                .last("LIMIT " + SUMMARY_WINDOW));
    java.util.Collections.reverse(recentMessages);

    for (Message msg : recentMessages) {
      context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
    }

    context.append("user: ").append(currentQuery);
    return context.toString();
  }

  private String generateSummaryWithAI(String summaryPrompt) {
    ChatClient chatClient = resolveChatClient();
    if (chatClient == null) return summaryPrompt;
    try {
      String result = chatClient.prompt().user(summaryPrompt).call().content();
      return result != null && !result.isBlank() ? result : summaryPrompt;
    } catch (Exception e) {
      log.warn("Failed to generate summary with AI, using prompt as fallback", e);
      return summaryPrompt;
    }
  }

  private ChatClient resolveChatClient() {
    if (chatClients != null && !chatClients.isEmpty()) {
      String provider = "deepseek";
      if (chatClients.containsKey(provider)) {
        return chatClients.get(provider);
      }
      return chatClients.values().iterator().next();
    }
    return null;
  }

  @Transactional
  public Conversation renameConversation(Long conversationId, String title) {
    Conversation conv = requireConversationOwner(conversationId);
    if (conv != null && title != null && !title.isBlank()) {
      conv.setTitle(title.trim());
      conversationMapper.updateById(conv);
    }
    return conv;
  }

  public void generateAutoTitle(Long conversationId, String firstResponse) {
    ChatClient chatClient = resolveChatClient();
    if (chatClient == null) return;
    try {
      String truncated =
          firstResponse.length() > 200 ? firstResponse.substring(0, 200) : firstResponse;
      String title =
          chatClient
              .prompt()
              .user("为以下AI助手的回答生成一个简短的标题（不超过20字，只返回标题文本，不要引号）:\n" + truncated)
              .call()
              .content();
      if (title != null && !title.isBlank()) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null && conv.getTitle() == null) {
          conv.setTitle(title.trim());
          conversationMapper.updateById(conv);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to generate auto title for conversation {}", conversationId, e);
    }
  }

  /** 构建对话历史字符串，用于 Query Rewrite。 格式为 "role: content" 每行一条消息，最多返回最近 10 条。 */
  private String buildConversationHistory(Long conversationId) {
    List<Message> recentMessages =
        messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getCreatedAt)
                .last("LIMIT " + SUMMARY_WINDOW));
    if (recentMessages.isEmpty()) {
      return "";
    }
    java.util.Collections.reverse(recentMessages);

    StringBuilder history = new StringBuilder();
    for (Message msg : recentMessages) {
      if (msg.getRole() != null && msg.getContent() != null) {
        history.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
      }
    }
    return history.toString().trim();
  }

  private void checkAndGenerateSummary(Long conversationId) {
    Long count =
        messageMapper.selectCount(
            new LambdaQueryWrapper<Message>().eq(Message::getConversationId, conversationId));

    if (count > SUMMARY_WINDOW * 2) {
      Conversation conv = conversationMapper.selectById(conversationId);
      if (conv != null && (conv.getSummary() == null || conv.getSummary().isBlank())) {
        List<Message> earlyMessages =
            messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conversationId)
                    .orderByAsc(Message::getCreatedAt)
                    .last("LIMIT " + SUMMARY_WINDOW));

        StringBuilder historyText = new StringBuilder();
        for (Message msg : earlyMessages) {
          historyText.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("messages", historyText.toString());

        String summaryPrompt = promptTemplateManager.render("summary-prompt", vars);
        String summary = generateSummaryWithAI(summaryPrompt);
        conv.setSummary(summary);
        conversationMapper.updateById(conv);
        log.info("Generated summary for conversation {}", conversationId);
      }
    }
  }
}
