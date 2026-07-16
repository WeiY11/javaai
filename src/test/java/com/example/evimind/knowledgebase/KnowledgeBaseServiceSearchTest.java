package com.example.evimind.knowledgebase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.KbMemberMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.KnowledgeBase;
import com.example.evimind.retrieval.HybridSearchService;
import com.example.evimind.retrieval.Reranker;
import com.example.evimind.retrieval.SearchResult;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceSearchTest {

  @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
  @Mock private KbMemberMapper kbMemberMapper;
  @Mock private HybridSearchService hybridSearchService;
  @Mock private Reranker reranker;

  private KnowledgeBaseService knowledgeBaseService;

  @BeforeEach
  void setUp() {
    GroupContext.set(11L, 3L, "USER");
    knowledgeBaseService = new KnowledgeBaseService(knowledgeBaseMapper, kbMemberMapper);
    ReflectionTestUtils.setField(knowledgeBaseService, "hybridSearchService", hybridSearchService);
    ReflectionTestUtils.setField(knowledgeBaseService, "reranker", reranker);
    ReflectionTestUtils.setField(knowledgeBaseService, "searchRerankerEnabled", true);
    ReflectionTestUtils.setField(knowledgeBaseService, "maxSearchTopK", 20);
    ReflectionTestUtils.setField(knowledgeBaseService, "searchCandidateMultiplier", 2);
  }

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void shouldSearchAccessibleKnowledgeBaseWithExpandedCandidateWindowAndRerank() {
    KnowledgeBase kb = new KnowledgeBase();
    kb.setId(7L);
    when(knowledgeBaseMapper.selectById(7L)).thenReturn(kb);
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);

    KnowledgeBaseSearchRequest request = new KnowledgeBaseSearchRequest();
    request.setQuery("DAG scheduling");
    request.setTopK(3);
    request.setConversationHistory("user: compare HEFT and PPO");

    List<SearchResult> candidates =
        List.of(
            new SearchResult("chunk_1", 10L, 7L, "weak", 0, 0.70, "rrf_fused"),
            new SearchResult("chunk_2", 11L, 7L, "strong", 1, 0.65, "rrf_fused"),
            new SearchResult("chunk_3", 12L, 7L, "tail", 2, 0.40, "rrf_fused"));
    List<SearchResult> reranked =
        List.of(
            new SearchResult("chunk_2", 11L, 7L, "strong", 1, 0.96, "rrf_fused+reranked"),
            new SearchResult("chunk_1", 10L, 7L, "weak", 0, 0.55, "rrf_fused+reranked"));

    when(hybridSearchService.search("DAG scheduling", 7L, 6, "user: compare HEFT and PPO"))
        .thenReturn(candidates);
    when(reranker.rerank("DAG scheduling", candidates, 3)).thenReturn(reranked);

    List<SearchResult> results = knowledgeBaseService.search(7L, request);

    assertEquals(reranked, results);
    verify(hybridSearchService).search("DAG scheduling", 7L, 6, "user: compare HEFT and PPO");
    verify(reranker).rerank("DAG scheduling", candidates, 3);
  }

  @Test
  void shouldRejectBlankSearchQueryBeforeCallingBackends() {
    KnowledgeBaseSearchRequest request = new KnowledgeBaseSearchRequest();
    request.setQuery("  ");

    assertThrows(IllegalArgumentException.class, () -> knowledgeBaseService.search(7L, request));
    verifyNoInteractions(knowledgeBaseMapper, kbMemberMapper, hybridSearchService, reranker);
  }

  @Test
  void shouldClampOversizedTopKAndSkipRerankerWhenDisabledByRequest() {
    KnowledgeBase kb = new KnowledgeBase();
    kb.setId(7L);
    when(knowledgeBaseMapper.selectById(7L)).thenReturn(kb);
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);

    KnowledgeBaseSearchRequest request = new KnowledgeBaseSearchRequest();
    request.setQuery("Lyapunov PPO");
    request.setTopK(100);
    request.setRerank(false);

    List<SearchResult> candidates =
        List.of(
            new SearchResult("chunk_1", 10L, 7L, "a", 0, 0.90, "rrf_fused"),
            new SearchResult("chunk_2", 11L, 7L, "b", 1, 0.80, "rrf_fused"));
    when(hybridSearchService.search("Lyapunov PPO", 7L, 40, null)).thenReturn(candidates);

    List<SearchResult> results = knowledgeBaseService.search(7L, request);

    assertEquals(candidates, results);
    verify(hybridSearchService).search("Lyapunov PPO", 7L, 40, null);
    verifyNoInteractions(reranker);
  }
}
