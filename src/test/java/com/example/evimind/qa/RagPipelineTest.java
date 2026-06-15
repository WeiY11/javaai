package com.example.evimind.qa;

import com.example.evimind.config.PromptTemplateManager;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.KbMemberMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.KnowledgeBase;
import com.example.evimind.retrieval.HybridSearchService;
import com.example.evimind.retrieval.Reranker;
import com.example.evimind.retrieval.SearchResult;
import org.junit.jupiter.api.AfterEach;
import org.springframework.ai.chat.client.ChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagPipelineTest {

    @Mock private HybridSearchService hybridSearchService;
    @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock private KbMemberMapper kbMemberMapper;
    @Mock private DocumentMapper documentMapper;
    @Mock private PromptTemplateManager promptTemplateManager;
    @Mock private Map<String, ChatClient> chatClients;
    @Mock private Reranker reranker;

    @InjectMocks
    private RagPipeline ragPipeline;

    @BeforeEach
    void setUp() {
        GroupContext.set(1L, 1L, "USER");
        // 禁用 reranker 以确保现有测试不受影响
        ReflectionTestUtils.setField(ragPipeline, "rerankerEnabled", false);
    }

    @AfterEach
    void tearDown() {
        GroupContext.clear();
    }

    @Test
    void shouldReturnInsufficientWhenNoResults() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setEvidenceThreshold(new BigDecimal("0.50"));

        when(kbMemberMapper.selectCount(any())).thenReturn(1L);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);
        when(hybridSearchService.search(anyString(), eq(1L), eq(10), isNull())).thenReturn(List.of());
        when(promptTemplateManager.render(eq("evidence-insufficient-prompt"), anyMap()))
                .thenReturn("抱歉，没有找到相关信息。");

        RagResponse response = ragPipeline.query("test query", 1L);

        assertEquals(RagResponse.EvidenceStatus.NO_RESULTS, response.getEvidenceStatus());
        assertEquals("抱歉，没有找到相关信息。", response.getAnswer());
        verify(chatClients, never()).get(anyString());
    }

    @Test
    void shouldReturnInsufficientWhenBelowThreshold() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setEvidenceThreshold(new BigDecimal("0.80"));

        List<SearchResult> results = List.of(
                new SearchResult("c1", 1L, 1L, "content", 0, 0.01, "rrf_fused")
        );

        when(kbMemberMapper.selectCount(any())).thenReturn(1L);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);
        when(hybridSearchService.search(anyString(), eq(1L), eq(10), isNull())).thenReturn(results);
        when(promptTemplateManager.render(eq("evidence-insufficient-prompt"), anyMap()))
                .thenReturn("证据不足");

        RagResponse response = ragPipeline.query("test query", 1L);

        assertEquals(RagResponse.EvidenceStatus.INSUFFICIENT, response.getEvidenceStatus());
        verify(chatClients, never()).get(anyString());
    }

    @Test
    void shouldTreatStrongTopEvidenceAsSufficientDespiteWeakTail() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setEvidenceThreshold(new BigDecimal("0.50"));

        List<SearchResult> results = List.of(
                new SearchResult("c1", 1L, 1L, "primary evidence", 0, 0.92, "rrf_fused"),
                new SearchResult("c2", 1L, 1L, "supporting tail", 1, 0.20, "rrf_fused"),
                new SearchResult("c3", 1L, 1L, "weak tail", 2, 0.10, "rrf_fused")
        );

        when(kbMemberMapper.selectCount(any())).thenReturn(1L);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);
        when(hybridSearchService.search(anyString(), eq(1L), eq(10), isNull())).thenReturn(results);
        when(promptTemplateManager.render(eq("evidence-sufficient-prompt"), anyMap()))
                .thenReturn("prompt");
        when(chatClients.isEmpty()).thenReturn(true);

        RagResponse response = ragPipeline.query("test query", 1L);

        assertEquals(RagResponse.EvidenceStatus.SUFFICIENT, response.getEvidenceStatus());
        assertEquals("AI model not available. Please configure an AI provider.", response.getAnswer());
    }

    @Test
    void shouldLimitEvidenceContextBeforeCallingModel() {
        ReflectionTestUtils.setField(ragPipeline, "maxEvidenceContextChars", 800);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setEvidenceThreshold(new BigDecimal("0.50"));

        String longContent = "evidence ".repeat(120);
        List<SearchResult> results = List.of(
                new SearchResult("c1", 1L, 1L, longContent + "first", 0, 0.95, "rrf_fused"),
                new SearchResult("c2", 1L, 1L, longContent + "second", 1, 0.90, "rrf_fused"),
                new SearchResult("c3", 1L, 1L, longContent + "third", 2, 0.85, "rrf_fused")
        );

        when(kbMemberMapper.selectCount(any())).thenReturn(1L);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);
        when(hybridSearchService.search(anyString(), eq(1L), eq(10), isNull())).thenReturn(results);
        when(promptTemplateManager.render(eq("evidence-sufficient-prompt"), anyMap()))
                .thenReturn("prompt");
        when(chatClients.isEmpty()).thenReturn(true);

        ragPipeline.query("test query", 1L);

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(promptTemplateManager).render(eq("evidence-sufficient-prompt"), varsCaptor.capture());
        String evidence = (String) varsCaptor.getValue().get("evidence");
        assertTrue(evidence.length() <= 800);
        assertTrue(evidence.contains("[来源1]"));
        assertFalse(evidence.contains("third"));
    }

    @Test
    void shouldReturnCitationsOnlyForBudgetSelectedEvidence() {
        ReflectionTestUtils.setField(ragPipeline, "maxEvidenceContextChars", 800);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setEvidenceThreshold(new BigDecimal("0.50"));

        String longContent = "evidence ".repeat(120);
        List<SearchResult> results = List.of(
                new SearchResult("c1", 1L, 1L, longContent + "alpha", 0, 0.95, "rrf_fused"),
                new SearchResult("c2", 2L, 1L, longContent + "beta", 0, 0.90, "rrf_fused"),
                new SearchResult("c3", 3L, 1L, longContent + "gamma", 0, 0.85, "rrf_fused")
        );
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

        when(kbMemberMapper.selectCount(any())).thenReturn(1L);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);
        when(hybridSearchService.search(anyString(), eq(1L), eq(10), isNull())).thenReturn(results);
        when(promptTemplateManager.render(eq("evidence-sufficient-prompt"), anyMap()))
                .thenReturn("prompt");
        when(chatClients.isEmpty()).thenReturn(false);
        when(chatClients.values()).thenReturn(List.of(chatClient));
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("answer");
        when(documentMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        RagResponse response = ragPipeline.query("alpha beta gamma", 1L);

        assertEquals("answer", response.getAnswer());
        assertEquals(1, response.getCitations().size());
        assertEquals(1L, response.getCitations().get(0).getDocumentId());
        ArgumentCaptor<Collection<Long>> docIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(documentMapper).selectBatchIds(docIdsCaptor.capture());
        assertEquals(List.of(1L), List.copyOf(docIdsCaptor.getValue()));
    }

    @Test
    void shouldThrowSecurityExceptionWhenNotMember() {
        when(kbMemberMapper.selectCount(any())).thenReturn(0L);

        assertThrows(SecurityException.class, () -> ragPipeline.query("test", 1L));
    }

    @Test
    void shouldThrowWhenKnowledgeBaseNotFound() {
        when(kbMemberMapper.selectCount(any())).thenReturn(1L);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> ragPipeline.query("test", 1L));
    }

    @Test
    void shouldApplyRerankerWhenEnabled() {
        ReflectionTestUtils.setField(ragPipeline, "rerankerEnabled", true);
        ReflectionTestUtils.setField(ragPipeline, "rerankerTopN", 5);
        ReflectionTestUtils.setField(ragPipeline, "maxEvidenceContextChars", 6000);

        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setEvidenceThreshold(new BigDecimal("0.30"));

        List<SearchResult> fusedResults = List.of(
                new SearchResult("c1", 1L, 1L, "content1", 0, 0.90, "rrf_fused"),
                new SearchResult("c2", 2L, 1L, "content2", 1, 0.80, "rrf_fused"),
                new SearchResult("c3", 3L, 1L, "content3", 2, 0.70, "rrf_fused")
        );
        // Reranker 将顺序反转（c3 最相关）
        List<SearchResult> rerankedResults = List.of(
                new SearchResult("c3", 3L, 1L, "content3", 2, 0.95, "rrf_fused+reranked"),
                new SearchResult("c1", 1L, 1L, "content1", 0, 0.60, "rrf_fused+reranked"),
                new SearchResult("c2", 2L, 1L, "content2", 1, 0.40, "rrf_fused+reranked")
        );

        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

        when(kbMemberMapper.selectCount(any())).thenReturn(1L);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);
        when(hybridSearchService.search(anyString(), eq(1L), eq(10), isNull())).thenReturn(fusedResults);
        when(reranker.rerank(eq("test query"), eq(fusedResults), eq(5))).thenReturn(rerankedResults);
        when(promptTemplateManager.render(eq("evidence-sufficient-prompt"), anyMap())).thenReturn("prompt");
        when(chatClients.isEmpty()).thenReturn(false);
        when(chatClients.values()).thenReturn(List.of(chatClient));
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("answer");
        when(documentMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        RagResponse response = ragPipeline.query("test query", 1L);

        assertEquals(RagResponse.EvidenceStatus.SUFFICIENT, response.getEvidenceStatus());
        assertEquals("answer", response.getAnswer());
        // 验证 reranker 被调用
        verify(reranker).rerank("test query", fusedResults, 5);
        // 验证 citations 来自 reranked 结果（c3 排第一，documentId = 3）
        assertNotNull(response.getCitations());
        assertEquals(3L, response.getCitations().get(0).getDocumentId());
    }
}
