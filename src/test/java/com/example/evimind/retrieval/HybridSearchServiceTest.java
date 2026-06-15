package com.example.evimind.retrieval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock private PgVectorSearchService pgVectorSearchService;
    @Mock private ElasticsearchSearchService elasticsearchSearchService;
    @Mock private RrfFusionService rrfFusionService;
    @Mock private QueryRewriteService queryRewriteService;

    @InjectMocks
    private HybridSearchService hybridSearchService;

    @Test
    void shouldNotWaitForSlowKeywordBackendWhenSemanticResultsAreAvailable() {
        ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 50L);
        List<SearchResult> semanticResults = List.of(
                new SearchResult("chunk_1", 1L, 1L, "semantic", 0, 0.90, "pgvector")
        );
        List<SearchResult> fusedResults = List.of(
                new SearchResult("chunk_1", 1L, 1L, "semantic", 0, 1.0, "rrf_fused")
        );

        when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
        when(pgVectorSearchService.search(eq("query"), eq(1L), eq(30))).thenReturn(semanticResults);
        when(elasticsearchSearchService.search(eq("query"), eq(1L), eq(30))).thenAnswer(invocation -> {
            Thread.sleep(500);
            return List.of();
        });
        when(rrfFusionService.fuse(eq(semanticResults), eq(List.of()), eq(10))).thenReturn(fusedResults);

        long started = System.nanoTime();
        List<SearchResult> results = hybridSearchService.search("query", 1L, 10);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(fusedResults, results);
        assertTrue(elapsedMillis < 250, "search should return after timeout instead of waiting for the slow backend");
    }

    @Test
    void shouldAskBackendsForExpandedCandidateWindow() {
        ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 1000L);
        when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
        when(pgVectorSearchService.search(eq("query"), eq(1L), eq(15))).thenReturn(List.of());
        when(elasticsearchSearchService.search(eq("query"), eq(1L), eq(15))).thenReturn(List.of());

        hybridSearchService.search("query", 1L, 5);

        verify(pgVectorSearchService).search("query", 1L, 15);
        verify(elasticsearchSearchService).search("query", 1L, 15);
    }

    @Test
    void shouldClampInvalidAndOversizedTopKAtSearchBoundary() {
        ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 1000L);
        when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
        when(pgVectorSearchService.search(eq("query"), eq(1L), eq(50))).thenReturn(List.of());
        when(elasticsearchSearchService.search(eq("query"), eq(1L), eq(50))).thenReturn(List.of());

        hybridSearchService.search("query", 1L, 500);

        verify(pgVectorSearchService).search("query", 1L, 50);
        verify(elasticsearchSearchService).search("query", 1L, 50);
    }

    @Test
    void shouldUseRewrittenQueryWhenConversationHistoryProvided() {
        ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 1000L);
        String history = "user: 什么是机器学习\nassistant: 机器学习是...";

        when(queryRewriteService.rewrite(eq("它的优点"), eq(history)))
                .thenReturn("机器学习的优点");
        when(pgVectorSearchService.search(eq("机器学习的优点"), eq(1L), eq(30)))
                .thenReturn(List.of());
        when(elasticsearchSearchService.search(eq("机器学习的优点"), eq(1L), eq(30)))
                .thenReturn(List.of());

        hybridSearchService.search("它的优点", 1L, 10, history);

        // 验证后端搜索引擎收到的是改写后的查询，而非原始查询
        verify(pgVectorSearchService).search("机器学习的优点", 1L, 30);
        verify(elasticsearchSearchService).search("机器学习的优点", 1L, 30);
        // 验证原始查询未被直接使用
        verify(pgVectorSearchService, never()).search("它的优点", 1L, 30);
    }
}
