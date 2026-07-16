package com.example.evimind.retrieval;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.model.entity.DocumentChunk;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(
    name = "custom.standalone.search.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class SimpleKeywordSearchService {

  private static final int MAX_QUERY_TERMS = 64;
  private static final int MAX_QUERY_TERM_CHARS = 256;
  private static final Pattern SEARCH_TERM_PATTERN =
      Pattern.compile("[^\\s,\\uFF0C\\u3002\\uFF01\\uFF1F!?]+");

  private static final Comparator<ScoredChunk> BEST_FIRST =
      Comparator.<ScoredChunk>comparingDouble(ScoredChunk::score)
          .reversed()
          .thenComparing(
              scored -> scored.chunk().getId(), Comparator.nullsLast(Comparator.naturalOrder()));

  @Autowired private DocumentChunkMapper documentChunkMapper;

  public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
    List<String> queryTerms = searchableTerms(query);
    if (queryTerms.isEmpty() || topK <= 0) {
      return List.of();
    }

    List<DocumentChunk> matchingChunks =
        documentChunkMapper.findActiveContainingAnyTerm(knowledgeBaseId, queryTerms);
    if (matchingChunks == null || matchingChunks.isEmpty()) {
      return List.of();
    }

    PriorityQueue<ScoredChunk> selected =
        new PriorityQueue<>(
            Math.max(1, Math.min(topK, matchingChunks.size())), BEST_FIRST.reversed());
    for (DocumentChunk chunk : matchingChunks) {
      double score = score(chunk, queryTerms);
      if (score <= 0) {
        continue;
      }
      ScoredChunk candidate = new ScoredChunk(chunk, score);
      if (selected.size() < topK) {
        selected.offer(candidate);
      } else if (BEST_FIRST.compare(candidate, selected.peek()) < 0) {
        selected.poll();
        selected.offer(candidate);
      }
    }

    List<SearchResult> results =
        selected.stream().sorted(BEST_FIRST).map(this::toSearchResult).toList();
    log.debug(
        "Simple keyword search returned {} results for KB {}", results.size(), knowledgeBaseId);
    return results;
  }

  private double score(DocumentChunk chunk, List<String> queryTerms) {
    String content =
        chunk != null && chunk.getContent() != null
            ? chunk.getContent().toLowerCase(Locale.ROOT)
            : "";
    double score = 0.0;
    for (String term : queryTerms) {
      int count = 0;
      int index = 0;
      while ((index = content.indexOf(term, index)) >= 0) {
        count++;
        index += term.length();
      }
      if (count > 0) {
        double termFrequency = (double) count / Math.max(1, content.length() / 50);
        score += termFrequency * Math.log(1 + 1.0 / count);
      }
    }
    return Math.min(score / queryTerms.size(), 1.0);
  }

  private SearchResult toSearchResult(ScoredChunk scored) {
    DocumentChunk chunk = scored.chunk();
    return new SearchResult(
        "chunk_" + chunk.getId(),
        chunk.getDocumentId(),
        chunk.getKnowledgeBaseId(),
        chunk.getContent(),
        chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0,
        scored.score(),
        "local_keyword");
  }

  private List<String> searchableTerms(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    LinkedHashSet<String> terms = new LinkedHashSet<>();
    Matcher matcher = SEARCH_TERM_PATTERN.matcher(query);
    while (terms.size() < MAX_QUERY_TERMS && matcher.find()) {
      int termLength = matcher.end() - matcher.start();
      if (termLength < 2 || termLength > MAX_QUERY_TERM_CHARS) {
        continue;
      }
      terms.add(matcher.group().toLowerCase(Locale.ROOT));
    }
    return List.copyOf(terms);
  }

  private record ScoredChunk(DocumentChunk chunk, double score) {}
}
