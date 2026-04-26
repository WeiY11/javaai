package com.example.javaai.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RrfFusionService {

    private static final int DEFAULT_K = 60;

    public List<SearchResult> fuse(List<SearchResult> semanticResults, List<SearchResult> keywordResults, int topN) {
        return fuse(semanticResults, keywordResults, topN, DEFAULT_K);
    }

    public List<SearchResult> fuse(List<SearchResult> semanticResults, List<SearchResult> keywordResults, int topN, int k) {
        Map<String, RrfEntry> entryMap = new LinkedHashMap<>();

        List<SearchResult> sortedSemantic = semanticResults.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .toList();
        for (int i = 0; i < sortedSemantic.size(); i++) {
            SearchResult r = sortedSemantic.get(i);
            entryMap.computeIfAbsent(r.getChunkId(), id -> new RrfEntry(r))
                    .addSemanticRank(i + 1, k);
        }

        List<SearchResult> sortedKeyword = keywordResults.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .toList();
        for (int i = 0; i < sortedKeyword.size(); i++) {
            SearchResult r = sortedKeyword.get(i);
            entryMap.computeIfAbsent(r.getChunkId(), id -> new RrfEntry(r))
                    .addKeywordRank(i + 1, k);
        }

        List<SearchResult> fused = entryMap.values().stream()
                .sorted(Comparator.comparingDouble(RrfEntry::getScore).reversed())
                .limit(topN)
                .map(entry -> {
                    SearchResult r = entry.result;
                    return new SearchResult(r.getChunkId(), r.getDocumentId(), r.getKnowledgeBaseId(),
                            r.getContent(), r.getChunkIndex(), entry.getScore(), "rrf_fused");
                })
                .collect(Collectors.toList());

        log.debug("RRF fusion: {} semantic + {} keyword -> {} fused results",
                semanticResults.size(), keywordResults.size(), fused.size());
        return fused;
    }

    private static class RrfEntry {
        final SearchResult result;
        double score = 0.0;
        boolean hasSemantic = false;
        boolean hasKeyword = false;

        RrfEntry(SearchResult result) {
            this.result = result;
        }

        void addSemanticRank(int rank, int k) {
            score += 1.0 / (k + rank);
            hasSemantic = true;
        }

        void addKeywordRank(int rank, int k) {
            score += 1.0 / (k + rank);
            hasKeyword = true;
        }

        double getScore() {
            return score;
        }
    }
}
