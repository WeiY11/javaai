package com.example.evimind.qa;

import com.example.evimind.config.PromptTemplateManager;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.KbMemberMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.dto.StreamEvent;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.KbMember;
import com.example.evimind.model.entity.KnowledgeBase;
import com.example.evimind.retrieval.HybridSearchService;
import com.example.evimind.retrieval.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagPipeline {

    @Autowired
    private HybridSearchService hybridSearchService;
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Autowired
    private KbMemberMapper kbMemberMapper;
    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private PromptTemplateManager promptTemplateManager;
    @Autowired
    private Map<String, ChatClient> chatClients;

    @Value("${custom.rag.max-evidence-context-chars:6000}")
    private int maxEvidenceContextChars = 6000;

    private final EvidencePortfolioSelector evidencePortfolioSelector = new EvidencePortfolioSelector();

    public RagResponse query(String userQuery, Long knowledgeBaseId) {
        requireKbMember(knowledgeBaseId);

        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new IllegalArgumentException("Knowledge base not found: " + knowledgeBaseId);
        }

        List<SearchResult> results = hybridSearchService.search(userQuery, knowledgeBaseId, 10);
        RagResponse response = new RagResponse();

        if (results.isEmpty()) {
            response.setEvidenceStatus(RagResponse.EvidenceStatus.NO_RESULTS);
            response.setAnswer(renderInsufficientPrompt(userQuery));
            return response;
        }

        if (!hasSufficientEvidence(results, kb.getEvidenceThreshold())) {
            response.setEvidenceStatus(RagResponse.EvidenceStatus.INSUFFICIENT);
            response.setAnswer(renderInsufficientPrompt(userQuery));
            return response;
        }

        response.setEvidenceStatus(RagResponse.EvidenceStatus.SUFFICIENT);

        List<SearchResult> evidencePortfolio = selectEvidencePortfolio(userQuery, results);
        String prompt = renderEvidencePrompt(userQuery, evidencePortfolio);
        ChatClient chatClient = resolveChatClient();
        if (chatClient == null) {
            response.setAnswer("AI model not available. Please configure an AI provider.");
            return response;
        }

        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        response.setAnswer(answer);
        response.setCitations(buildCitations(evidencePortfolio));

        return response;
    }

    public Flux<String> streamQuery(String userQuery, Long knowledgeBaseId, String modelProvider) {
        return streamQuery(userQuery, knowledgeBaseId, modelProvider, null, null, null, null, null, null);
    }

    public Flux<String> streamQuery(String userQuery, Long knowledgeBaseId, String modelProvider,
                                    Double temperature, Double topP, Integer maxTokens,
                                    String modelName, Boolean thinking, String reasoningEffort) {
        requireKbMember(knowledgeBaseId);

        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            return Flux.just(StreamEvent.error("Knowledge base not found: " + knowledgeBaseId));
        }

        List<SearchResult> results;
        try {
            results = hybridSearchService.search(userQuery, knowledgeBaseId, 10);
        } catch (Exception e) {
            log.error("Hybrid search failed", e);
            return Flux.just(StreamEvent.error("Search failed: " + e.getMessage()));
        }

        if (results.isEmpty() || !hasSufficientEvidence(results, kb.getEvidenceThreshold())) {
            return Flux.just(
                    StreamEvent.token(renderInsufficientPrompt(userQuery)),
                    StreamEvent.done(null)
            );
        }

        ChatClient chatClient = resolveChatClient(modelProvider);
        if (chatClient == null) {
            return Flux.just(StreamEvent.error("AI model not available. Please configure an AI provider."));
        }

        List<SearchResult> evidencePortfolio = selectEvidencePortfolio(userQuery, results);
        String prompt = renderEvidencePrompt(userQuery, evidencePortfolio);
        List<RagResponse.Citation> citations = buildCitations(evidencePortfolio);
        String citationsJson = StreamEvent.citations(citations);

        var promptSpec = chatClient.prompt().user(prompt);
        var optionsBuilder = org.springframework.ai.openai.OpenAiChatOptions.builder();
        String actualModelName = buildModelName(modelName, thinking, reasoningEffort);
        if (actualModelName != null) {
            optionsBuilder.withModel(actualModelName);
        }
        if (temperature != null) optionsBuilder.withTemperature(temperature.floatValue());
        if (topP != null) optionsBuilder.withTopP(topP.floatValue());
        if (maxTokens != null) optionsBuilder.withMaxTokens(maxTokens);

        promptSpec = promptSpec.options(optionsBuilder.build());

        return promptSpec
                .stream()
                .content()
                .map(StreamEvent::token)
                .concatWithValues(citationsJson, StreamEvent.done(null));
    }

    private String renderInsufficientPrompt(String userQuery) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("query", userQuery);
        return promptTemplateManager.render("evidence-insufficient-prompt", vars);
    }

    private String renderEvidencePrompt(String userQuery, List<SearchResult> evidencePortfolio) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("evidence", buildBudgetedEvidenceContext(evidencePortfolio));
        vars.put("query", userQuery);
        return promptTemplateManager.render("evidence-sufficient-prompt", vars);
    }

    private String buildModelName(String modelName, Boolean thinking, String reasoningEffort) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        if (!Boolean.TRUE.equals(thinking)) {
            return modelName;
        }
        StringBuilder actual = new StringBuilder(modelName).append("|thinking:enabled");
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            actual.append("|effort:").append(reasoningEffort);
        }
        return actual.toString();
    }

    private boolean hasSufficientEvidence(List<SearchResult> results, BigDecimal threshold) {
        if (results.isEmpty()) return false;
        if (threshold == null) return true;
        return evidenceConfidence(results) >= threshold.doubleValue();
    }

    private double evidenceConfidence(List<SearchResult> results) {
        List<SearchResult> ranked = results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .toList();
        double topScore = ranked.get(0).getScore();
        int supportCount = Math.min(3, ranked.size());
        double topSupportAverage = ranked.stream()
                .limit(supportCount)
                .mapToDouble(SearchResult::getScore)
                .average()
                .orElse(0.0);
        return Math.max(0.0, Math.min(1.0, 0.70 * topScore + 0.30 * topSupportAverage));
    }

    private void requireKbMember(Long knowledgeBaseId) {
        Long userId = GroupContext.getUserId();
        if (userId == null) return;
        Long count = kbMemberMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbMember>()
                        .eq(KbMember::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(KbMember::getUserId, userId)
        );
        if (count == 0) {
            throw new SecurityException("Access denied: you are not a member of knowledge base " + knowledgeBaseId);
        }
    }

    public boolean isKbMember(Long knowledgeBaseId) {
        Long userId = GroupContext.getUserId();
        if (userId == null) return false;
        Long count = kbMemberMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbMember>()
                        .eq(KbMember::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(KbMember::getUserId, userId)
        );
        return count > 0;
    }

    private ChatClient resolveChatClient() {
        if (chatClients != null && !chatClients.isEmpty()) {
            return chatClients.values().iterator().next();
        }
        return null;
    }

    private ChatClient resolveChatClient(String provider) {
        if (chatClients != null && provider != null && chatClients.containsKey(provider)) {
            return chatClients.get(provider);
        }
        return resolveChatClient();
    }

    private List<RagResponse.Citation> buildCitations(List<SearchResult> results) {
        Set<Long> docIds = results.stream()
                .map(SearchResult::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> fileNames = new HashMap<>();
        if (!docIds.isEmpty()) {
            List<Document> docs = documentMapper.selectBatchIds(docIds);
            if (docs != null) {
                for (Document doc : docs) {
                    if (doc != null) {
                        fileNames.put(doc.getId(), doc.getFileName());
                    }
                }
            }
        }

        List<RagResponse.Citation> citations = new ArrayList<>();
        for (SearchResult r : results) {
            RagResponse.Citation citation = new RagResponse.Citation();
            citation.setDocumentId(r.getDocumentId());
            citation.setFileName(fileNames.getOrDefault(r.getDocumentId(), null));
            citation.setChunkIndex(r.getChunkIndex());
            citation.setScore(r.getScore());
            citations.add(citation);
        }
        return citations;
    }

    private List<SearchResult> selectEvidencePortfolio(String userQuery, List<SearchResult> results) {
        int budget = Math.max(400, maxEvidenceContextChars);
        return evidencePortfolioSelector.select(userQuery, results, budget);
    }

    private String buildBudgetedEvidenceContext(List<SearchResult> evidencePortfolio) {
        StringBuilder sb = new StringBuilder();
        int budget = Math.max(400, maxEvidenceContextChars);
        for (int i = 0; i < evidencePortfolio.size(); i++) {
            String block = evidenceBlock(i, evidencePortfolio.get(i));
            int remaining = budget - sb.length();
            if (remaining <= 0) break;
            if (block.length() <= remaining) {
                sb.append(block);
                continue;
            }
            if (sb.isEmpty()) {
                sb.append(block, 0, Math.max(0, remaining));
            }
            break;
        }
        return sb.toString();
    }

    private String evidenceBlock(int index, SearchResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("[来源").append(index + 1).append("] 文档ID=").append(r.getDocumentId())
                .append(" 切片#").append(r.getChunkIndex())
                .append(" 置信度=").append(String.format("%.3f", r.getScore()))
                .append(" 检索源=").append(r.getSource()).append("\n");
        sb.append(r.getContent()).append("\n\n");
        return sb.toString();
    }
}
