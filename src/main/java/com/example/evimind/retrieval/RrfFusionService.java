package com.example.evimind.retrieval;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RrfFusionService {

  private static final int DEFAULT_K = 60;

  public List<SearchResult> fuse(
      List<SearchResult> semanticResults, List<SearchResult> keywordResults, int topN) {
    return fuse(semanticResults, keywordResults, topN, DEFAULT_K);
  }

  public List<SearchResult> fuse(
      List<SearchResult> semanticResults, List<SearchResult> keywordResults, int topN, int k) {
    List<SearchResult> normSemantic = normalizeScores(semanticResults);
    List<SearchResult> normKeyword = normalizeScores(keywordResults);

    Map<String, RrfEntry> entryMap = new LinkedHashMap<>();
    int activeSources = 0;
    if (!normSemantic.isEmpty()) activeSources++;
    if (!normKeyword.isEmpty()) activeSources++;

    List<SearchResult> sortedSemantic =
        normSemantic.stream()
            .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
            .toList();
    for (int i = 0; i < sortedSemantic.size(); i++) {
      SearchResult r = sortedSemantic.get(i);
      entryMap
          .computeIfAbsent(fusionKey(r), id -> new RrfEntry(r))
          .addSemanticRank(i + 1, k, r.getScore());
    }

    List<SearchResult> sortedKeyword =
        normKeyword.stream()
            .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
            .toList();
    for (int i = 0; i < sortedKeyword.size(); i++) {
      SearchResult r = sortedKeyword.get(i);
      entryMap
          .computeIfAbsent(fusionKey(r), id -> new RrfEntry(r))
          .addKeywordRank(i + 1, k, r.getScore());
    }

    double idealRrf = activeSources * (1.0 / (k + 1));
    List<SearchResult> fused =
        entryMap.values().stream()
            .peek(entry -> entry.calculateConfidence(idealRrf))
            .sorted(Comparator.comparingDouble(RrfEntry::getScore).reversed())
            .limit(topN)
            .map(
                entry -> {
                  SearchResult r = entry.result;
                  return new SearchResult(
                      r.getChunkId(),
                      r.getDocumentId(),
                      r.getKnowledgeBaseId(),
                      r.getContent(),
                      r.getChunkIndex(),
                      entry.getScore(),
                      "rrf_fused");
                })
            .collect(Collectors.toList());

    log.debug(
        "RRF fusion: {} semantic + {} keyword -> {} fused results",
        semanticResults.size(),
        keywordResults.size(),
        fused.size());
    return fused;
  }

  private String fusionKey(SearchResult result) {
    if (result.getDocumentId() != null) {
      return result.getDocumentId() + "#" + result.getChunkIndex();
    }
    return result.getChunkId();
  }

  private List<SearchResult> normalizeScores(List<SearchResult> results) {
    if (results.isEmpty()) return results;

    double min = results.stream().mapToDouble(SearchResult::getScore).min().orElse(0.0);
    double max = results.stream().mapToDouble(SearchResult::getScore).max().orElse(1.0);

    if (max == min) {
      return results.stream()
          .map(
              r ->
                  new SearchResult(
                      r.getChunkId(),
                      r.getDocumentId(),
                      r.getKnowledgeBaseId(),
                      r.getContent(),
                      r.getChunkIndex(),
                      1.0,
                      r.getSource()))
          .collect(Collectors.toList());
    }

    return results.stream()
        .map(
            r ->
                new SearchResult(
                    r.getChunkId(),
                    r.getDocumentId(),
                    r.getKnowledgeBaseId(),
                    r.getContent(),
                    r.getChunkIndex(),
                    (r.getScore() - min) / (max - min),
                    r.getSource()))
        .collect(Collectors.toList());
  }

  private static class RrfEntry {
    final SearchResult result;
    double rrfScore = 0.0;
    double normalizedScoreSum = 0.0;
    int sourceHits = 0;
    double score = 0.0;

    RrfEntry(SearchResult result) {
      this.result = result;
    }

    void addSemanticRank(int rank, int k, double normalizedScore) {
      addRank(rank, k, normalizedScore);
    }

    void addKeywordRank(int rank, int k, double normalizedScore) {
      addRank(rank, k, normalizedScore);
    }

    private void addRank(int rank, int k, double normalizedScore) {
      rrfScore += 1.0 / (k + rank);
      normalizedScoreSum += clamp(normalizedScore);
      sourceHits++;
    }

    void calculateConfidence(double idealRrf) {
      double rankConfidence = idealRrf > 0.0 ? rrfScore / idealRrf : 0.0;
      double sourceConfidence = sourceHits > 0 ? normalizedScoreSum / sourceHits : 0.0;
      score = clamp(0.75 * rankConfidence + 0.25 * sourceConfidence);
    }

    double getScore() {
      return score;
    }

    private static double clamp(double value) {
      if (value < 0.0) return 0.0;
      if (value > 1.0) return 1.0;
      return value;
    }
  }
}
