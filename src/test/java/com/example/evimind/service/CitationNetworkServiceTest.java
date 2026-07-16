package com.example.evimind.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
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

import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.CitationLinkMapper;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.knowledgebase.KnowledgeBaseService;
import com.example.evimind.model.entity.CitationLink;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.KnowledgeBase;

class CitationNetworkServiceTest {

  @BeforeEach
  void setUp() {
    GroupContext.set(1L, 1L, "USER");
  }

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void citationGraphShouldRejectAnInaccessibleKnowledgeBase() {
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    when(knowledgeBaseService.getById(7L))
        .thenThrow(new SecurityException("Access denied: you are not a member of this knowledge base"));
    CitationNetworkService service =
        new CitationNetworkService(
            mock(CitationLinkMapper.class),
            mock(DocumentMapper.class),
            knowledgeBaseService,
            mock(DocumentPermissionService.class));

    assertThatThrownBy(() -> service.getCitationGraph(7L))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("not a member");
  }

  @Test
  void citationGraphShouldExcludeDocumentsWithoutReadPermission() {
    CitationLinkMapper citationLinkMapper = mock(CitationLinkMapper.class);
    DocumentMapper documentMapper = mock(DocumentMapper.class);
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    DocumentPermissionService documentPermissionService = mock(DocumentPermissionService.class);
    Document restrictedDocument = new Document();
    restrictedDocument.setId(42L);
    restrictedDocument.setKnowledgeBaseId(7L);
    restrictedDocument.setFileName("restricted-paper.pdf");
    CitationLink citationLink = new CitationLink();
    citationLink.setId(3L);
    citationLink.setDocumentId(42L);
    citationLink.setKnowledgeBaseId(7L);
    citationLink.setCitedDoi("10.1000/restricted");
    when(knowledgeBaseService.getById(7L)).thenReturn(new KnowledgeBase());
    when(documentMapper.selectList(any())).thenReturn(List.of(restrictedDocument));
    when(citationLinkMapper.findByKnowledgeBaseId(7L)).thenReturn(List.of(citationLink));
    when(documentPermissionService.hasRestrictions(42L)).thenReturn(true);
    when(documentPermissionService.hasPermission(42L, 1L, DocumentPermissionService.PERM_READ))
        .thenReturn(false);

    CitationNetworkService service =
        new CitationNetworkService(
            citationLinkMapper, documentMapper, knowledgeBaseService, documentPermissionService);

    Map<String, Object> graph = service.getCitationGraph(7L);

    assertThat((List<?>) graph.get("nodes")).isEmpty();
    assertThat((List<?>) graph.get("edges")).isEmpty();
  }

  @Test
  void documentCitationsShouldRejectDocumentsWithoutReadPermission() {
    CitationLinkMapper citationLinkMapper = mock(CitationLinkMapper.class);
    DocumentMapper documentMapper = mock(DocumentMapper.class);
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    DocumentPermissionService documentPermissionService = mock(DocumentPermissionService.class);
    Document restrictedDocument = new Document();
    restrictedDocument.setId(42L);
    restrictedDocument.setKnowledgeBaseId(7L);
    when(documentMapper.selectById(42L)).thenReturn(restrictedDocument);
    when(knowledgeBaseService.getById(7L)).thenReturn(new KnowledgeBase());
    when(documentPermissionService.hasRestrictions(42L)).thenReturn(true);
    when(documentPermissionService.hasPermission(42L, 1L, DocumentPermissionService.PERM_READ))
        .thenReturn(false);

    CitationNetworkService service =
        new CitationNetworkService(
            citationLinkMapper, documentMapper, knowledgeBaseService, documentPermissionService);

    assertThatThrownBy(() -> service.getCitationsForDocument(42L))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("requires READ permission");

    verify(citationLinkMapper, never()).findByDocumentId(42L);
  }
}
