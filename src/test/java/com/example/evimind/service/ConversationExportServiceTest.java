package com.example.evimind.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.evimind.assistant.ConversationService;
import com.example.evimind.config.PromptTemplateManager;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.ConversationMapper;
import com.example.evimind.mapper.MessageMapper;
import com.example.evimind.model.entity.Conversation;
import com.example.evimind.qa.RagPipeline;

class ConversationExportServiceTest {

  private final ConversationMapper conversationMapper = mock(ConversationMapper.class);
  private final MessageMapper messageMapper = mock(MessageMapper.class);

  @BeforeEach
  void setUp() {
    GroupContext.set(1L, 1L, "USER");
  }

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void exportAsMarkdownShouldRejectAConversationOwnedByAnotherUser() {
    Conversation conversation = new Conversation();
    conversation.setId(42L);
    conversation.setUserId(2L);
    when(conversationMapper.selectById(42L)).thenReturn(conversation);

    ConversationExportService service = createService();
    assertThatThrownBy(() -> service.exportAsMarkdown(42L))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("do not own this conversation");

    verifyNoInteractions(messageMapper);
  }

  @Test
  void exportAsJsonShouldRejectAConversationOwnedByAnotherUser() {
    Conversation conversation = new Conversation();
    conversation.setId(42L);
    conversation.setUserId(2L);
    when(conversationMapper.selectById(42L)).thenReturn(conversation);

    ConversationExportService service = createService();
    assertThatThrownBy(() -> service.exportAsJson(42L))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("do not own this conversation");

    verifyNoInteractions(messageMapper);
  }

  private ConversationExportService createService() {
    ConversationService conversationService =
        new ConversationService(
            conversationMapper,
            messageMapper,
            mock(PromptTemplateManager.class),
            mock(RagPipeline.class),
            Map.of());
    return new ConversationExportService(conversationService, messageMapper);
  }
}
