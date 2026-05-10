package com.example.evimind.qa;

import com.example.evimind.config.PromptTemplateManager;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.KbMemberMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.KnowledgeBase;
import com.example.evimind.retrieval.HybridSearchService;
import com.example.evimind.retrieval.SearchResult;
import org.junit.jupiter.api.AfterEach;
import org.springframework.ai.chat.client.ChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    @InjectMocks
    private RagPipeline ragPipeline;

    @BeforeEach
    void setUp() {
        GroupContext.set(1L, 1L, "USER");
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
        when(hybridSearchService.search(anyString(), eq(1L), eq(10))).thenReturn(List.of());
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
        when(hybridSearchService.search(anyString(), eq(1L), eq(10))).thenReturn(results);
        when(promptTemplateManager.render(eq("evidence-insufficient-prompt"), anyMap()))
                .thenReturn("证据不足");

        RagResponse response = ragPipeline.query("test query", 1L);

        assertEquals(RagResponse.EvidenceStatus.INSUFFICIENT, response.getEvidenceStatus());
        verify(chatClients, never()).get(anyString());
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
}
