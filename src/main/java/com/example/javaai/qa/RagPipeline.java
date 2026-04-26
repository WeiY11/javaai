package com.example.javaai.qa;

import com.example.javaai.config.PromptTemplateManager;
import com.example.javaai.mapper.KnowledgeBaseMapper;
import com.example.javaai.model.entity.KnowledgeBase;
import com.example.javaai.retrieval.HybridSearchService;
import com.example.javaai.retrieval.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RagPipeline {

    @Autowired
    private HybridSearchService hybridSearchService;
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Autowired
    private PromptTemplateManager promptTemplateManager;
    @Autowired
    private Map<String, ChatClient> chatClients;

    public RagResponse query(String userQuery, Long knowledgeBaseId) {
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

        List<RagResponse.Citation> citations = new ArrayList<>();
        for (SearchResult r : results) {
            RagResponse.Citation citation = new RagResponse.Citation();
            citation.setDocumentId(r.getDocumentId());
            citation.setChunkIndex(r.getChunkIndex());
            citation.setScore(r.getScore());
            citations.add(citation);
        }
        response.setCitations(citations);

        return response;
    }

    private ChatClient resolveChatClient() {
        if (chatClients != null && !chatClients.isEmpty()) {
            return chatClients.values().iterator().next();
        }
        return null;
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
