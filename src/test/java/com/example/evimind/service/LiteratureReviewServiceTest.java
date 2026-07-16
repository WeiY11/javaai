package com.example.evimind.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.example.evimind.config.PromptTemplateManager;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.knowledgebase.KnowledgeBaseService;
import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.model.entity.Document;

class LiteratureReviewServiceTest {

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    GroupContext.set(1L, 1L, "USER");
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void generateReviewShouldRejectAnInaccessibleKnowledgeBase() {
    Executor directExecutor = Runnable::run;
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    when(knowledgeBaseService.getById(7L))
        .thenThrow(new SecurityException("Access denied: you are not a member of this knowledge base"));
    LiteratureReviewService service =
        new LiteratureReviewService(
            mock(DocumentMapper.class),
            mock(DocumentChunkMapper.class),
            knowledgeBaseService,
            mock(DocumentPermissionService.class),
            mock(PromptTemplateManager.class),
            Map.<String, ChatClient>of(),
            directExecutor);

    assertThatThrownBy(() -> service.generateReview(7L, "retrieval augmented generation"))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("not a member");
  }

  @Test
  void generateReviewShouldExcludeDocumentsWithoutReadPermission() {
    DocumentMapper documentMapper = mock(DocumentMapper.class);
    DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    DocumentPermissionService documentPermissionService = mock(DocumentPermissionService.class);
    Document restrictedDocument = new Document();
    restrictedDocument.setId(42L);
    restrictedDocument.setKnowledgeBaseId(7L);
    restrictedDocument.setDoi("10.1000/restricted");
    when(knowledgeBaseService.getById(7L)).thenReturn(new com.example.evimind.model.entity.KnowledgeBase());
    when(documentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(restrictedDocument));
    when(documentPermissionService.hasRestrictions(42L)).thenReturn(true);
    when(documentPermissionService.hasPermission(42L, 1L, DocumentPermissionService.PERM_READ))
        .thenReturn(false);

    LiteratureReviewService service =
        new LiteratureReviewService(
            documentMapper,
            documentChunkMapper,
            knowledgeBaseService,
            documentPermissionService,
            mock(PromptTemplateManager.class),
            Map.<String, ChatClient>of(),
            Runnable::run);

    service.generateReview(7L, "retrieval augmented generation");

    verify(documentChunkMapper, never()).selectList(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void generateReviewShouldNotExposeModelFailureDetails() {
    DocumentMapper documentMapper = mock(DocumentMapper.class);
    DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    PromptTemplateManager promptTemplateManager = mock(PromptTemplateManager.class);
    ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
    Document paper = new Document();
    paper.setId(42L);
    paper.setKnowledgeBaseId(7L);
    paper.setDoi("10.1000/example");

    when(knowledgeBaseService.getById(7L)).thenReturn(new com.example.evimind.model.entity.KnowledgeBase());
    when(documentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(paper));
    when(documentChunkMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    when(promptTemplateManager.render(org.mockito.ArgumentMatchers.eq("literature-review-prompt"), org.mockito.ArgumentMatchers.anyMap()))
        .thenReturn("confidential-review-prompt");
    when(chatClient.prompt().user("confidential-review-prompt").call().content())
        .thenThrow(new RuntimeException("provider rejected confidential-review-prompt"));

    LiteratureReviewService service =
        new LiteratureReviewService(
            documentMapper,
            documentChunkMapper,
            knowledgeBaseService,
            mock(DocumentPermissionService.class),
            promptTemplateManager,
            Map.of("deepseek", chatClient),
            Runnable::run);

    assertEquals(
        "Literature review generation failed. Please try again.",
        service.generateReview(7L, "secure retrieval"));
  }
}
