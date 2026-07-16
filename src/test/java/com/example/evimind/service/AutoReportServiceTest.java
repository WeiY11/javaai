package com.example.evimind.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.example.evimind.mapper.ConversationMapper;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.MessageMapper;
import com.example.evimind.knowledgebase.KnowledgeBaseService;
import com.example.evimind.model.entity.KnowledgeBase;

class AutoReportServiceTest {

  @Test
  void generateReportShouldRejectAKnowledgeBaseTheCurrentUserCannotAccess() {
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    when(knowledgeBaseService.getById(42L))
        .thenThrow(new SecurityException("Access denied: you are not a member of this knowledge base"));

    Executor directExecutor = Runnable::run;
    AutoReportService service =
        new AutoReportService(
            knowledgeBaseService,
            mock(DocumentMapper.class),
            mock(ConversationMapper.class),
            mock(MessageMapper.class),
            Map.<String, ChatClient>of(),
            directExecutor);

    assertThatThrownBy(() -> service.generateReport(42L, "weekly"))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("not a member");
  }

  @Test
  void generateReportShouldRejectMembersWhoDoNotOwnTheKnowledgeBase() {
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    KnowledgeBase knowledgeBase = new KnowledgeBase();
    knowledgeBase.setId(42L);
    when(knowledgeBaseService.getById(42L)).thenReturn(knowledgeBase);
    when(knowledgeBaseService.isOwner(42L)).thenReturn(false);
    DocumentMapper documentMapper = mock(DocumentMapper.class);
    ConversationMapper conversationMapper = mock(ConversationMapper.class);
    MessageMapper messageMapper = mock(MessageMapper.class);

    AutoReportService service =
        new AutoReportService(
            knowledgeBaseService,
            documentMapper,
            conversationMapper,
            messageMapper,
            Map.<String, ChatClient>of(),
            Runnable::run);

    assertThatThrownBy(() -> service.generateReport(42L, "weekly"))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("knowledge base owner");
    verifyNoInteractions(documentMapper, conversationMapper, messageMapper);
  }
}
