package com.example.evimind.assistant;

import com.example.evimind.retrieval.HybridSearchService;
import com.example.evimind.retrieval.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentToolsTest {

    @Test
    void shouldClampKbSearchTopKAndLimitToolOutput() {
        HybridSearchService hybridSearchService = mock(HybridSearchService.class);
        AgentTools agentTools = new AgentTools(hybridSearchService);
        String longContent = "content ".repeat(1000);
        when(hybridSearchService.search("query", 1L, 10)).thenReturn(List.of(
                new SearchResult("c1", 1L, 1L, longContent, 0, 0.95, "rrf_fused"),
                new SearchResult("c2", 1L, 1L, longContent, 1, 0.90, "rrf_fused")
        ));

        AgentTools.KbSearchResponse response = agentTools.kbSearch()
                .apply(new AgentTools.KbSearchRequest("query", 1L, 100));

        verify(hybridSearchService).search("query", 1L, 10);
        assertNull(response.error());
        assertTrue(response.results().length() <= 4000);
        assertTrue(response.results().contains("文档ID=1"));
    }
}
