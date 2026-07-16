package com.example.evimind.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.evimind.identity.GroupContext;
import com.example.evimind.knowledgebase.KnowledgeBaseService;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.KnowledgeBase;

class CitationServiceTest {

  private final DocumentMapper documentMapper = mock(DocumentMapper.class);
  private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
  private final DocumentPermissionService documentPermissionService = mock(DocumentPermissionService.class);

  @BeforeEach
  void setUp() {
    GroupContext.set(1L, 1L, "USER");
  }

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void generateBibtexShouldRejectDocumentsFromAnInaccessibleKnowledgeBase() {
    Document document = new Document();
    document.setId(42L);
    document.setKnowledgeBaseId(7L);
    document.setFileName("private-paper.pdf");
    when(documentMapper.selectById(42L)).thenReturn(document);
    when(knowledgeBaseService.getById(7L))
        .thenThrow(new SecurityException("Access denied: you are not a member of this knowledge base"));

    CitationService service = createService();

    assertThatThrownBy(() -> service.generateBibtex(List.of(42L)))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("not a member");
  }

  @Test
  void generateApaShouldRejectAnExplicitlyRestrictedDocument() {
    Document document = new Document();
    document.setId(42L);
    document.setKnowledgeBaseId(7L);
    document.setFileName("restricted-paper.pdf");
    when(documentMapper.selectById(42L)).thenReturn(document);
    when(knowledgeBaseService.getById(7L)).thenReturn(new KnowledgeBase());
    when(documentPermissionService.hasRestrictions(42L)).thenReturn(true);
    doThrow(new SecurityException("Access denied: document requires READ permission"))
        .when(documentPermissionService)
        .requirePermission(42L, 1L, DocumentPermissionService.PERM_READ);

    CitationService service = createService();

    assertThatThrownBy(() -> service.generateApa(List.of(42L)))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("requires READ permission");
  }

  private CitationService createService() {
    return new CitationService(documentMapper, knowledgeBaseService, documentPermissionService);
  }
}
