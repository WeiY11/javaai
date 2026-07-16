package com.example.evimind.assistant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import com.example.evimind.config.PromptTemplateManager;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.ConversationMapper;
import com.example.evimind.mapper.MessageMapper;
import com.example.evimind.model.entity.Conversation;
import com.example.evimind.model.entity.Message;
import com.example.evimind.model.dto.StreamEvent;
import com.example.evimind.qa.RagPipeline;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.core.publisher.Flux;

class ConversationServiceTest {

  private final ConversationMapper conversationMapper = mock(ConversationMapper.class);
  private final Logger logger = (Logger) LoggerFactory.getLogger(ConversationService.class);
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    GroupContext.set(1L, 1L, "USER");
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logAppender);
    logAppender.stop();
    GroupContext.clear();
  }

  @Test
  void createConversationShouldRejectAnUnavailableExplicitProvider() {
    ConversationService service =
        new ConversationService(
            conversationMapper,
            mock(MessageMapper.class),
            mock(PromptTemplateManager.class),
            mock(RagPipeline.class),
            Map.of("zhipu", mock(ChatClient.class)));

    assertThatThrownBy(() -> service.createConversation(1L, "deepseek"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AI model provider is not available");

    verify(conversationMapper, never()).insert(any(Conversation.class));
  }

  @Test
  void streamMessageShouldRejectUnavailableLegacyProviderBeforePersistingUserMessage() {
    MessageMapper messageMapper = mock(MessageMapper.class);
    RagPipeline ragPipeline = mock(RagPipeline.class);
    Conversation conversation = new Conversation();
    conversation.setId(42L);
    conversation.setUserId(1L);
    conversation.setKnowledgeBaseId(7L);
    conversation.setModelProvider("retired-provider");

    when(conversationMapper.selectById(42L)).thenReturn(conversation);
    when(ragPipeline.isKbMember(7L)).thenReturn(true);
    when(ragPipeline.streamQuery(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Flux.just(StreamEvent.token("backend response")));

    ConversationService service =
        new ConversationService(
            conversationMapper,
            messageMapper,
            mock(PromptTemplateManager.class),
            ragPipeline,
            Map.of("deepseek", mock(ChatClient.class)));

    assertEquals(
        List.of(StreamEvent.error("AI model not available. Please configure an AI provider.")),
        service.streamMessage(42L, "hello").collectList().block());

    verify(messageMapper, never()).insert(any(Message.class));
  }

  @Test
  void addMessageShouldRejectClientSuppliedAssistantMessages() {
    MessageMapper messageMapper = mock(MessageMapper.class);
    Conversation conversation = new Conversation();
    conversation.setId(42L);
    conversation.setUserId(1L);
    when(conversationMapper.selectById(42L)).thenReturn(conversation);
    when(messageMapper.selectCount(any())).thenReturn(0L);

    ConversationService service =
        new ConversationService(
            conversationMapper,
            messageMapper,
            mock(PromptTemplateManager.class),
            mock(RagPipeline.class),
            Map.of("deepseek", mock(ChatClient.class)));

    assertThatThrownBy(() -> service.addMessage(42L, "assistant", "forged", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only user messages");

    verify(messageMapper, never()).insert(any(Message.class));
  }

  @Test
  void summaryGenerationFailureDoesNotExposePromptContentInLogs() {
    String sensitivePrompt = "confidential-research-prompt";
    MessageMapper messageMapper = mock(MessageMapper.class);
    PromptTemplateManager promptTemplateManager = mock(PromptTemplateManager.class);
    ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
    Conversation conversation = new Conversation();
    conversation.setId(42L);
    conversation.setUserId(1L);
    Message earlyMessage = new Message();
    earlyMessage.setRole("user");
    earlyMessage.setContent("earlier research question");

    when(conversationMapper.selectById(42L)).thenReturn(conversation);
    when(messageMapper.selectCount(any())).thenReturn(21L);
    when(messageMapper.selectList(any())).thenReturn(List.of(earlyMessage));
    when(promptTemplateManager.render("summary-prompt", Map.of("messages", "user: earlier research question\n")))
        .thenReturn(sensitivePrompt);
    when(chatClient.prompt().user(sensitivePrompt).call().content())
        .thenThrow(new RuntimeException("provider rejected " + sensitivePrompt));

    ConversationService service =
        new ConversationService(
            conversationMapper,
            messageMapper,
            promptTemplateManager,
            mock(RagPipeline.class),
            Map.of("deepseek", chatClient));

    service.addMessage(42L, "user", "new research question", null, null);

    ILoggingEvent event =
        logAppender.list.stream().filter(entry -> entry.getLevel() == Level.WARN).findFirst().orElseThrow();
    assertEquals(
        "Failed to generate summary with AI, using prompt as fallback (RuntimeException)",
        event.getFormattedMessage());
    assertNull(event.getThrowableProxy());
  }
}
