package com.example.evimind.qa;

import com.example.evimind.retrieval.SearchResult;

import java.util.*;
import java.util.regex.Pattern;

public class EvidencePortfolioSelector {

    private static final int MIN_CONTEXT_BUDGET = 400;
    private static final double MIN_MARGINAL_VALUE_AFTER_FIRST = 0.40;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s\\p{Punct}，。！？；：、（）【】《》“”‘’]+");

    public List<SearchResult> select(String query, List<SearchResult> candidates, int maxContextChars) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int budget = Math.max(MIN_CONTEXT_BUDGET, maxContextChars);
        List<SearchResult> pool = deduplicate(candidates).stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .toList();
        Set<String> queryTerms = tokenize(query);
        List<SearchResult> selected = new ArrayList<>();
        Set<String> coveredTerms = new HashSet<>();
        Set<Long> selectedDocs = new HashSet<>();
        int usedChars = 0;

        while (selected.size() < pool.size()) {
            CandidateScore best = null;
            for (SearchResult candidate : pool) {
                if (selected.contains(candidate)) continue;
                int candidateChars = estimateBlockChars(candidate);
                if (!selected.isEmpty() && usedChars + candidateChars > budget) continue;

                CandidateScore score = scoreCandidate(candidate, selected, selectedDocs, queryTerms, coveredTerms);
                if (!selected.isEmpty()
                        && !queryTerms.isEmpty()
                        && score.coverageGain == 0.0
                        && score.diversityBonus == 0.0) {
                    continue;
                }
                if (best == null || score.value > best.value) {
                    best = score;
                }
            }

            if (best == null) break;
            if (!selected.isEmpty() && best.value < MIN_MARGINAL_VALUE_AFTER_FIRST) break;
            selected.add(best.result);
            usedChars += estimateBlockChars(best.result);
            selectedDocs.add(best.result.getDocumentId());
            coveredTerms.addAll(matchedQueryTerms(best.result, queryTerms));

            if (usedChars >= budget) break;
        }

        return selected;
    }

    private List<SearchResult> deduplicate(List<SearchResult> candidates) {
        Map<String, SearchResult> bestByChunk = new LinkedHashMap<>();
        for (SearchResult candidate : candidates) {
            String key = candidate.getDocumentId() + "#" + candidate.getChunkIndex();
            SearchResult existing = bestByChunk.get(key);
            if (existing == null || candidate.getScore() > existing.getScore()) {
                bestByChunk.put(key, candidate);
            }
        }
        return new ArrayList<>(bestByChunk.values());
    }

    private CandidateScore scoreCandidate(SearchResult candidate,
                                          List<SearchResult> selected,
                                          Set<Long> selectedDocs,
                                          Set<String> queryTerms,
                                          Set<String> coveredTerms) {
        double confidence = clamp(candidate.getScore());
        double coverageGain = coverageGain(candidate, queryTerms, coveredTerms);
        double diversityBonus = selectedDocs.contains(candidate.getDocumentId()) ? 0.0 : 1.0;
        double redundancyPenalty = redundancy(candidate, selected);
        double value = 0.62 * confidence + 0.22 * coverageGain + 0.12 * diversityBonus - 0.24 * redundancyPenalty;
        return new CandidateScore(candidate, value, coverageGain, diversityBonus);
    }

    private double coverageGain(SearchResult candidate, Set<String> queryTerms, Set<String> coveredTerms) {
        if (queryTerms.isEmpty()) return 0.0;
        Set<String> matched = matchedQueryTerms(candidate, queryTerms);
        matched.removeAll(coveredTerms);
        return (double) matched.size() / queryTerms.size();
    }

    private Set<String> matchedQueryTerms(SearchResult result, Set<String> queryTerms) {
        Set<String> contentTerms = tokenize(result.getContent());
        Set<String> matched = new HashSet<>();
        for (String term : queryTerms) {
            if (contentTerms.contains(term)) {
                matched.add(term);
            }
        }
        return matched;
    }

    private double redundancy(SearchResult candidate, List<SearchResult> selected) {
        if (selected.isEmpty()) return 0.0;
        Set<String> candidateTerms = tokenize(candidate.getContent());
        double maxOverlap = 0.0;
        for (SearchResult selectedResult : selected) {
            Set<String> selectedTerms = tokenize(selectedResult.getContent());
            maxOverlap = Math.max(maxOverlap, jaccard(candidateTerms, selectedTerms));
        }
        return maxOverlap;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> terms = new LinkedHashSet<>();
        for (String raw : TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (raw.length() >= 2) {
                terms.add(raw);
                addCjkNgrams(raw, terms);
            }
        }
        return terms;
    }

    private void addCjkNgrams(String raw, Set<String> terms) {
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (isCjk(ch)) {
                cjk.append(ch);
            }
        }
        for (int i = 0; i + 1 < cjk.length(); i++) {
            terms.add(cjk.substring(i, i + 2));
        }
    }

    private boolean isCjk(char ch) {
        Character.UnicodeScript script = Character.UnicodeScript.of(ch);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private int estimateBlockChars(SearchResult result) {
        int contentLength = result.getContent() != null ? result.getContent().length() : 0;
        return contentLength + 96;
    }

    private double clamp(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private record CandidateScore(SearchResult result, double value, double coverageGain, double diversityBonus) {}
}
