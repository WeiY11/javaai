package com.example.evimind.qa;

import com.example.evimind.retrieval.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvidencePortfolioSelectorTest {

    private final EvidencePortfolioSelector selector = new EvidencePortfolioSelector();

    @Test
    void shouldPreferDiverseEvidenceCoverageOverRedundantNearDuplicate() {
        List<SearchResult> candidates = List.of(
                new SearchResult("c1", 1L, 1L,
                        "Graph neural retrieval needs calibrated evidence selection.",
                        0, 0.95, "rrf_fused"),
                new SearchResult("c2", 1L, 1L,
                        "Graph neural retrieval needs calibrated evidence selection.",
                        1, 0.93, "rrf_fused"),
                new SearchResult("c3", 2L, 1L,
                        "Citation grounding improves answer faithfulness under a limited context budget.",
                        0, 0.82, "rrf_fused")
        );

        List<SearchResult> selected = selector.select(
                "graph neural calibrated citation grounding", candidates, 600);

        assertEquals(List.of("c1", "c3"), selected.stream().map(SearchResult::getChunkId).toList());
    }

    @Test
    void shouldRespectContextBudgetWhenSelectingPortfolio() {
        String longChunk = "budget ".repeat(120);
        List<SearchResult> candidates = List.of(
                new SearchResult("c1", 1L, 1L, longChunk + " alpha", 0, 0.95, "rrf_fused"),
                new SearchResult("c2", 2L, 1L, longChunk + " beta", 0, 0.90, "rrf_fused"),
                new SearchResult("c3", 3L, 1L, "short gamma evidence", 0, 0.70, "rrf_fused")
        );

        List<SearchResult> selected = selector.select("alpha beta gamma", candidates, 900);

        assertEquals(1, selected.size());
        assertEquals("c1", selected.get(0).getChunkId());
    }

    @Test
    void shouldUseChineseCharacterNgramsForCoverage() {
        List<SearchResult> candidates = List.of(
                new SearchResult("c1", 1L, 1L, "证据选择策略可以降低幻觉。", 0, 0.90, "rrf_fused"),
                new SearchResult("c2", 1L, 1L, "模型推理速度与缓存命中有关。", 1, 0.98, "rrf_fused"),
                new SearchResult("c3", 2L, 1L, "引用约束机制提高回答可验证性。", 0, 0.75, "rrf_fused")
        );

        List<SearchResult> selected = selector.select("证据引用", candidates, 1000);

        assertEquals(List.of("c1", "c3"), selected.stream().map(SearchResult::getChunkId).toList());
    }
}
