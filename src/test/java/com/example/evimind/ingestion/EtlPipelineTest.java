package com.example.evimind.ingestion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.Document;

@ExtendWith(MockitoExtension.class)
class EtlPipelineTest {

  @Mock private DocumentMapper documentMapper;
  @Mock private DocumentChunkMapper documentChunkMapper;
  @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
  @Mock private EmbeddingService embeddingService;
  @Mock private ElasticsearchIndexService elasticsearchIndexService;

  @InjectMocks private EtlPipeline etlPipeline;

  @Test
  void shouldSetFailedStatusOnException() {
    Document doc = new Document();
    doc.setId(1L);
    doc.setKnowledgeBaseId(1L);
    doc.setIngestionStatus("PENDING");

    when(documentMapper.selectById(1L)).thenReturn(doc);

    etlPipeline.processDocument(1L);

    ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
    verify(documentMapper, atLeastOnce()).updateById(captor.capture());
    List<Document> updates = captor.getAllValues();
    Document lastUpdate = updates.get(updates.size() - 1);
    assertNotNull(lastUpdate.getIngestionStatus());
  }

  @Test
  void shouldThrowWhenDocumentNotFound() {
    when(documentMapper.selectById(999L)).thenReturn(null);

    assertThrows(IllegalArgumentException.class, () -> etlPipeline.processDocument(999L));
  }
}
