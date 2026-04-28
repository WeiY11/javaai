package com.example.javaai.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RrfFusionServiceTest {

    private final RrfFusionService rrfFusionService = new RrfFusionService();

    @Test
    void shouldFuseResultsWithNormalizedScores() {
        List<SearchResult> semantic = List.of(
                new SearchResult("c1", 1L, 1L, "content1", 0, 0.95, "pgvector"),
                new SearchResult("c2", 2L, 1L, "content2", 1, 0.80, "pgvector")
        );
        List<SearchResult> keyword = List.of(
                new SearchResult("c2", 2L, 1L, "content2", 1, 2.5, "elasticsearch"),
                new SearchResult("c1", 1L, 1L, "content1", 0, 1.0, "elasticsearch")
        );

        List<SearchResult> fused = rrfFusionService.fuse(semantic, keyword, 5);

        assertNotNull(fused);
        assertFalse(fused.isEmpty());
        assertTrue(fused.size() <= 5);
        assertEquals("rrf_fused", fused.get(0).getSource());
    }

    @Test
    void shouldReturnEmptyWhenBothEmpty() {
        List<SearchResult> fused = rrfFusionService.fuse(List.of(), List.of(), 10);
        assertTrue(fused.isEmpty());
    }

    @Test
    void shouldWorkWithOnlySemanticResults() {
        List<SearchResult> semantic = List.of(
                new SearchResult("c1", 1L, 1L, "content1", 0, 0.90, "pgvector")
        );
        List<SearchResult> fused = rrfFusionService.fuse(semantic, List.of(), 10);
        assertEquals(1, fused.size());
        assertEquals("c1", fused.get(0).getChunkId());
    }

    @Test
    void shouldWorkWithOnlyKeywordResults() {
        List<SearchResult> keyword = List.of(
                new SearchResult("c3", 3L, 1L, "content3", 2, 5.0, "elasticsearch")
        );
        List<SearchResult> fused = rrfFusionService.fuse(List.of(), keyword, 10);
        assertEquals(1, fused.size());
        assertEquals("c3", fused.get(0).getChunkId());
    }

    @Test
    void shouldRespectTopN() {
        List<SearchResult> semantic = List.of(
                new SearchResult("c1", 1L, 1L, "c1", 0, 0.99, "pgvector"),
                new SearchResult("c2", 2L, 1L, "c2", 1, 0.90, "pgvector"),
                new SearchResult("c3", 3L, 1L, "c3", 2, 0.80, "pgvector")
        );
        List<SearchResult> fused = rrfFusionService.fuse(semantic, List.of(), 2);
        assertEquals(2, fused.size());
    }

    @Test
    void shouldNormalizeScoresToZeroOneRange() {
        List<SearchResult> raw = List.of(
                new SearchResult("c1", 1L, 1L, "low", 0, 0.1, "pgvector"),
                new SearchResult("c2", 2L, 1L, "high", 1, 0.9, "pgvector")
        );
        List<SearchResult> fused = rrfFusionService.fuse(raw, List.of(), 10);
        assertFalse(fused.isEmpty());
    }
}
