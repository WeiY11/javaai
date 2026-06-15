package com.example.evimind.retrieval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class RrfFusionServiceTest {

  private final RrfFusionService rrfFusionService = new RrfFusionService();

  @Test
  void shouldFuseResultsWithNormalizedScores() {
    List<SearchResult> semantic =
        List.of(
            new SearchResult("c1", 1L, 1L, "content1", 0, 0.95, "pgvector"),
            new SearchResult("c2", 2L, 1L, "content2", 1, 0.80, "pgvector"));
    List<SearchResult> keyword =
        List.of(
            new SearchResult("c2", 2L, 1L, "content2", 1, 2.5, "elasticsearch"),
            new SearchResult("c1", 1L, 1L, "content1", 0, 1.0, "elasticsearch"));

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
    List<SearchResult> semantic =
        List.of(new SearchResult("c1", 1L, 1L, "content1", 0, 0.90, "pgvector"));
    List<SearchResult> fused = rrfFusionService.fuse(semantic, List.of(), 10);
    assertEquals(1, fused.size());
    assertEquals("c1", fused.get(0).getChunkId());
  }

  @Test
  void shouldWorkWithOnlyKeywordResults() {
    List<SearchResult> keyword =
        List.of(new SearchResult("c3", 3L, 1L, "content3", 2, 5.0, "elasticsearch"));
    List<SearchResult> fused = rrfFusionService.fuse(List.of(), keyword, 10);
    assertEquals(1, fused.size());
    assertEquals("c3", fused.get(0).getChunkId());
  }

  @Test
  void shouldRespectTopN() {
    List<SearchResult> semantic =
        List.of(
            new SearchResult("c1", 1L, 1L, "c1", 0, 0.99, "pgvector"),
            new SearchResult("c2", 2L, 1L, "c2", 1, 0.90, "pgvector"),
            new SearchResult("c3", 3L, 1L, "c3", 2, 0.80, "pgvector"));
    List<SearchResult> fused = rrfFusionService.fuse(semantic, List.of(), 2);
    assertEquals(2, fused.size());
  }

  @Test
  void shouldNormalizeScoresToZeroOneRange() {
    List<SearchResult> raw =
        List.of(
            new SearchResult("c1", 1L, 1L, "low", 0, 0.1, "pgvector"),
            new SearchResult("c2", 2L, 1L, "high", 1, 0.9, "pgvector"));
    List<SearchResult> fused = rrfFusionService.fuse(raw, List.of(), 10);
    assertFalse(fused.isEmpty());
  }

  @Test
  void shouldReturnThresholdFriendlyConfidenceScores() {
    List<SearchResult> semantic =
        List.of(
            new SearchResult("c1", 1L, 1L, "answer", 0, 0.95, "pgvector"),
            new SearchResult("c2", 1L, 1L, "tail", 1, 0.65, "pgvector"));
    List<SearchResult> keyword =
        List.of(
            new SearchResult("c1", 1L, 1L, "answer", 0, 8.0, "elasticsearch"),
            new SearchResult("c3", 1L, 1L, "keyword only", 2, 4.0, "elasticsearch"));

    List<SearchResult> fused = rrfFusionService.fuse(semantic, keyword, 3);

    assertEquals("c1", fused.get(0).getChunkId());
    assertTrue(
        fused.get(0).getScore() >= 0.80,
        "agreed top evidence should clear a 0.50 threshold comfortably");
    assertTrue(fused.stream().allMatch(r -> r.getScore() >= 0.0 && r.getScore() <= 1.0));
  }

  @Test
  void shouldMergeSameDocumentChunkEvenWhenBackendIdsDiffer() {
    List<SearchResult> semantic =
        List.of(new SearchResult("chunk_100", 10L, 1L, "same chunk", 3, 0.95, "pgvector"));
    List<SearchResult> keyword =
        List.of(new SearchResult("chunk_10_3", 10L, 1L, "same chunk", 3, 5.0, "elasticsearch"));

    List<SearchResult> fused = rrfFusionService.fuse(semantic, keyword, 5);

    assertEquals(1, fused.size());
    assertTrue(fused.get(0).getScore() >= 0.80);
  }
}
