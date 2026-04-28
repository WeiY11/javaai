package com.example.javaai.ingestion;

import com.example.javaai.mapper.DocumentChunkMapper;
import com.example.javaai.mapper.DocumentMapper;
import com.example.javaai.mapper.KnowledgeBaseMapper;
import com.example.javaai.model.entity.Document;
import com.example.javaai.model.entity.KnowledgeBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EtlPipelineTest {

    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentChunkMapper documentChunkMapper;
    @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock private EmbeddingService embeddingService;
    @Mock private ElasticsearchIndexService elasticsearchIndexService;

    @InjectMocks
    private EtlPipeline etlPipeline;

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
