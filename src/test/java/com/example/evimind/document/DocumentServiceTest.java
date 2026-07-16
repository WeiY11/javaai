package com.example.evimind.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.example.evimind.identity.GroupContext;
import com.example.evimind.ingestion.EtlPipeline;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.KbMemberMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.Document;
import com.example.evimind.service.DocumentPermissionService;
import com.example.evimind.storage.LocalFileStorageService;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

  @Mock private DocumentMapper documentMapper;
  @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
  @Mock private KbMemberMapper kbMemberMapper;
  @Mock private LocalFileStorageService localFileStorageService;
  @Mock private EtlPipeline etlPipeline;
  @Mock private DocumentPermissionService documentPermissionService;

  @InjectMocks private DocumentService documentService;

  @BeforeEach
  void setUp() {
    GroupContext.set(11L, 3L, "USER");
  }

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void shouldFailClosedWhenUserContextMissing() {
    GroupContext.clear();

    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () -> documentService.listByKnowledgeBase(7L, 1, 10));
  }

  @Test
  void shouldStoreUploadUnderOpaquePathWithoutOriginalFilename() throws Exception {
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);
    doAnswer(
            invocation -> {
              Document doc = invocation.getArgument(0);
              doc.setId(101L);
              return 1;
            })
        .when(documentMapper)
        .insert(any(Document.class));

    MockMultipartFile file =
        new MockMultipartFile("file", "paper.pdf", "application/pdf", "content".getBytes());

    Document uploaded = documentService.upload(file, 7L);

    ArgumentCaptor<String> objectName = ArgumentCaptor.forClass(String.class);
    verify(localFileStorageService)
        .uploadFile(objectName.capture(), any(InputStream.class), eq(file.getSize()), eq("application/pdf"));
    assertTrue(objectName.getValue().matches("7/[0-9a-f\\-]{36}/[0-9a-f\\-]{36}\\.pdf"));
    assertFalse(objectName.getValue().contains("paper.pdf"));
    assertEquals("paper.pdf", uploaded.getFileName());
    assertEquals("UPLOADED", uploaded.getIngestionStatus());
  }

  @Test
  void shouldRejectRetryWhileIngestionIsRunning() {
    Document doc = new Document();
    doc.setId(9L);
    doc.setKnowledgeBaseId(7L);
    doc.setIngestionStatus("INDEXING");
    when(documentMapper.selectById(9L)).thenReturn(doc);
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);

    assertThrows(IllegalStateException.class, () -> documentService.retryIngestion(9L));

    verify(documentMapper, never()).updateById(any(Document.class));
    verifyNoInteractions(etlPipeline);
  }

  @Test
  void shouldRejectRetryAfterMaxAttempts() {
    Document doc = new Document();
    doc.setId(9L);
    doc.setKnowledgeBaseId(7L);
    doc.setIngestionStatus("FAILED");
    doc.setRetryCount(3);
    when(documentMapper.selectById(9L)).thenReturn(doc);
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);

    assertThrows(IllegalStateException.class, () -> documentService.retryIngestion(9L));

    verify(documentMapper, never()).updateById(any(Document.class));
    verifyNoInteractions(etlPipeline);
  }

  @Test
  void shouldRejectRestrictedDocumentDetailsWithoutReadPermission() {
    Document doc = document(9L, 7L, "COMPLETED");
    when(documentMapper.selectById(9L)).thenReturn(doc);
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);
    when(documentPermissionService.hasRestrictions(9L)).thenReturn(true);
    doThrow(new SecurityException("denied"))
        .when(documentPermissionService)
        .requirePermission(9L, 11L, DocumentPermissionService.PERM_READ);

    assertThrows(SecurityException.class, () -> documentService.getById(9L));

    verify(documentPermissionService)
        .requirePermission(9L, 11L, DocumentPermissionService.PERM_READ);
  }

  @Test
  void shouldRejectDeletingRestrictedDocumentWithoutAdminPermission() {
    Document doc = document(9L, 7L, "COMPLETED");
    when(documentMapper.selectById(9L)).thenReturn(doc);
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);
    when(documentPermissionService.hasRestrictions(9L)).thenReturn(true);
    doThrow(new SecurityException("denied"))
        .when(documentPermissionService)
        .requirePermission(9L, 11L, DocumentPermissionService.PERM_ADMIN);

    assertThrows(SecurityException.class, () -> documentService.delete(9L));

    verify(documentPermissionService)
        .requirePermission(9L, 11L, DocumentPermissionService.PERM_ADMIN);
    verifyNoInteractions(etlPipeline);
  }

  @Test
  void shouldRejectRetryingRestrictedDocumentWithoutWritePermission() {
    Document doc = document(9L, 7L, "FAILED");
    doc.setRetryCount(0);
    when(documentMapper.selectById(9L)).thenReturn(doc);
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);
    when(documentPermissionService.hasRestrictions(9L)).thenReturn(true);
    doThrow(new SecurityException("denied"))
        .when(documentPermissionService)
        .requirePermission(9L, 11L, DocumentPermissionService.PERM_WRITE);

    assertThrows(SecurityException.class, () -> documentService.retryIngestion(9L));

    verify(documentPermissionService)
        .requirePermission(9L, 11L, DocumentPermissionService.PERM_WRITE);
    verify(documentMapper, never()).updateById(any(Document.class));
    verifyNoInteractions(etlPipeline);
  }

  @Test
  void shouldUsePermissionAwareQueryForNonAdminDocumentLists() {
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);

    documentService.listByKnowledgeBase(7L, 1, 10);

    verify(documentMapper).selectAccessiblePage(any(), eq(7L), eq(11L));
    verify(documentMapper, never()).selectPage(any(), any());
  }

  private Document document(Long id, Long knowledgeBaseId, String ingestionStatus) {
    Document doc = new Document();
    doc.setId(id);
    doc.setKnowledgeBaseId(knowledgeBaseId);
    doc.setIngestionStatus(ingestionStatus);
    return doc;
  }
}
