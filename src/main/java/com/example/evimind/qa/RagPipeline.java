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
            Map<String, Object> vars = new HashMap<>();
            vars.put("query", userQuery);
            response.setAnswer(promptTemplateManager.render("evidence-insufficient-prompt", vars));
            return response;
        }

        double avgScore = results.stream()
                .mapToDouble(SearchResult::getScore)
                .average()
                .orElse(0.0);
        BigDecimal threshold = kb.getEvidenceThreshold();

        if (threshold != null && avgScore < threshold.doubleValue()) {
            response.setEvidenceStatus(RagResponse.EvidenceStatus.INSUFFICIENT);
            Map<String, Object> vars = new HashMap<>();
            vars.put("query", userQuery);
            response.setAnswer(promptTemplateManager.render("evidence-insufficient-prompt", vars));
            return response;
        }

        response.setEvidenceStatus(RagResponse.EvidenceStatus.SUFFICIENT);

        String context = buildContext(results);
        Map<String, Object> vars = new HashMap<>();
        vars.put("evidence", context);
        vars.put("query", userQuery);
        String prompt = promptTemplateManager.render("evidence-sufficient-prompt", vars);

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
        response.setCitations(buildCitations(results));

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

        if (results.isEmpty()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("query", userQuery);
            String refusal = promptTemplateManager.render("evidence-insufficient-prompt", vars);
            return Flux.just(
                    StreamEvent.token(refusal),
                    StreamEvent.done(null)
            );
        }

        double avgScore = results.stream()
                .mapToDouble(SearchResult::getScore)
                .average()
                .orElse(0.0);
        BigDecimal threshold = kb.getEvidenceThreshold();

        if (threshold != null && avgScore < threshold.doubleValue()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("query", userQuery);
            String refusal = promptTemplateManager.render("evidence-insufficient-prompt", vars);
            return Flux.just(
                    StreamEvent.token(refusal),
                    StreamEvent.done(null)
            );
        }

        String context = buildContext(results);
        Map<String, Object> vars = new HashMap<>();
        vars.put("evidence", context);
        vars.put("query", userQuery);
        String prompt = promptTemplateManager.render("evidence-sufficient-prompt", vars);

        ChatClient chatClient = resolveChatClient(modelProvider);
        if (chatClient == null) {
            return Flux.just(StreamEvent.error("AI model not available. Please configure an AI provider."));
        }

        List<RagResponse.Citation> citations = buildCitations(results);
        String citationsJson = StreamEvent.citations(citations);

        var promptSpec = chatClient.prompt().user(prompt);

        String actualModelName = modelName;
        if (Boolean.TRUE.equals(thinking)) {
            actualModelName += "|thinking:enabled";
            if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                actualModelName += "|effort:" + reasoningEffort;
            }
        }

        var optionsBuilder = org.springframework.ai.openai.OpenAiChatOptions.builder();
        if (actualModelName != null && !actualModelName.isBlank()) {
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
        Set<Long> docIds = results.stream().map(SearchResult::getDocumentId).collect(Collectors.toSet());
        Map<Long, String> fileNames = new HashMap<>();
        for (Long docId : docIds) {
            Document doc = documentMapper.selectById(docId);
            if (doc != null) {
                fileNames.put(docId, doc.getFileName());
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

    private String buildContext(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("[来源").append(i + 1).append("] 文档ID=").append(r.getDocumentId())
                    .append(" 切片#").append(r.getChunkIndex())
                    .append(" (评分=").append(String.format("%.3f", r.getScore())).append(")\n");
            sb.append(r.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
