package com.example.evimind.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.model.entity.DocumentChunk;

class SimpleKeywordSearchServiceTest {

  @Test
  void shouldSkipDatabaseScanWhenQueryHasNoSearchableTerms() {
    DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
    SimpleKeywordSearchService searchService = new SimpleKeywordSearchService();
    ReflectionTestUtils.setField(searchService, "documentChunkMapper", documentChunkMapper);

    assertTrue(searchService.search(" , ! a ", 1L, 10).isEmpty());

    verifyNoInteractions(documentChunkMapper);
  }

  @Test
  void shouldSkipDatabaseScanWhenTopKIsNotPositive() {
    DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
    SimpleKeywordSearchService searchService = serviceWith(documentChunkMapper);

    assertTrue(searchService.search("alpha", 7L, 0).isEmpty());

    verifyNoInteractions(documentChunkMapper);
  }

  @Test
  void shouldUseOnePrefilteredQueryAndKeepOnlyHighestScoringResults() {
    DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
    SimpleKeywordSearchService searchService = serviceWith(documentChunkMapper);
    when(documentChunkMapper.findActiveContainingAnyTerm(7L, List.of("alpha", "beta")))
        .thenReturn(
            List.of(
                chunk(3L, 13L, "alpha alpha beta"),
                chunk(1L, 11L, "alpha beta"),
                chunk(2L, 12L, "beta")));

    List<SearchResult> results = searchService.search("Alpha beta", 7L, 2);

    assertEquals(List.of("chunk_3", "chunk_1"), chunkIds(results));
    assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    verify(documentChunkMapper)
        .findActiveContainingAnyTerm(7L, List.of("alpha", "beta"));
    verify(documentChunkMapper, never()).selectList(any());
    verifyNoMoreInteractions(documentChunkMapper);
  }

  @Test
  void shouldOrderEqualScoresByChunkIdAscending() {
    DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
    SimpleKeywordSearchService searchService = serviceWith(documentChunkMapper);
    when(documentChunkMapper.findActiveContainingAnyTerm(7L, List.of("alpha")))
        .thenReturn(List.of(chunk(2L, 12L, "alpha"), chunk(1L, 11L, "alpha")));

    List<SearchResult> results = searchService.search("alpha", 7L, 2);

    assertEquals(List.of("chunk_1", "chunk_2"), chunkIds(results));
  }

  @Test
  void shouldBoundDynamicSqlTermsForOversizedQueries() {
    DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
    SimpleKeywordSearchService searchService = serviceWith(documentChunkMapper);
    List<String> allTerms = IntStream.range(0, 70).mapToObj(i -> "term" + i).toList();
    List<String> boundedTerms = allTerms.subList(0, 64);
    when(documentChunkMapper.findActiveContainingAnyTerm(7L, boundedTerms))
        .thenReturn(List.of());

    assertTrue(searchService.search(String.join(" ", allTerms), 7L, 10).isEmpty());

    verify(documentChunkMapper).findActiveContainingAnyTerm(7L, boundedTerms);
    verifyNoMoreInteractions(documentChunkMapper);
  }

  @Test
  void shouldSkipOversizedTermsBeforeBuildingDynamicSql() {
    DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
    SimpleKeywordSearchService searchService = serviceWith(documentChunkMapper);
    when(documentChunkMapper.findActiveContainingAnyTerm(7L, List.of("alpha")))
        .thenReturn(List.of());

    assertTrue(searchService.search("x".repeat(257) + " alpha", 7L, 10).isEmpty());

    verify(documentChunkMapper).findActiveContainingAnyTerm(7L, List.of("alpha"));
    verifyNoMoreInteractions(documentChunkMapper);
  }

  private static SimpleKeywordSearchService serviceWith(
      DocumentChunkMapper documentChunkMapper) {
    SimpleKeywordSearchService searchService = new SimpleKeywordSearchService();
    ReflectionTestUtils.setField(searchService, "documentChunkMapper", documentChunkMapper);
    return searchService;
  }

  private static List<String> chunkIds(List<SearchResult> results) {
    return results.stream().map(SearchResult::getChunkId).toList();
  }

  private static DocumentChunk chunk(Long id, Long documentId, String content) {
    DocumentChunk chunk = new DocumentChunk();
    chunk.setId(id);
    chunk.setDocumentId(documentId);
    chunk.setKnowledgeBaseId(7L);
    chunk.setChunkIndex(Math.toIntExact(id));
    chunk.setContent(content);
    return chunk;
  }
}
