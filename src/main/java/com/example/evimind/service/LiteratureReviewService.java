package com.example.evimind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.config.PromptTemplateManager;
import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureReviewService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final PromptTemplateManager promptTemplateManager;
    private final Map<String, ChatClient> chatClients;

    private static final long LLM_TIMEOUT_MS = 120_000;
    private static final int MAX_CHUNKS_PER_DOC = 5;
    private static final int MAX_PAPERS = 20;

    /**
     * 基于知识库中的学术论文生成结构化文献综述。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param topic           研究主题
     * @return LLM 生成的文献综述文本
     */
    public String generateReview(Long knowledgeBaseId, String topic) {
        // 1. 查询知识库中有论文元数据（DOI、作者、年份）的文档
        List<Document> papers = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                        .and(w -> w
                                .isNotNull(Document::getDoi)
                                .or()
                                .isNotNull(Document::getAuthors)
                                .or()
                                .isNotNull(Document::getPublicationYear)
                        )
                        .orderByAsc(Document::getPublicationYear)
        );

        if (papers.isEmpty()) {
            log.warn("No papers with metadata found in knowledge base {}", knowledgeBaseId);
            return "当前知识库中没有找到包含论文元数据（DOI、作者、年份）的学术论文。请先上传论文并确保元数据已提取。";
        }

        // 限制论文数量，避免 prompt 过长
        if (papers.size() > MAX_PAPERS) {
            papers = papers.subList(0, MAX_PAPERS);
        }

        // 2. 获取这些文档的内容片段
        List<Long> docIds = papers.stream().map(Document::getId).collect(Collectors.toList());
        List<DocumentChunk> chunks = documentChunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>()
                        .in(DocumentChunk::getDocumentId, docIds)
                        .orderByAsc(DocumentChunk::getDocumentId)
                        .orderByAsc(DocumentChunk::getChunkIndex)
        );

        // 按文档分组，每篇文档最多取 MAX_CHUNKS_PER_DOC 个片段
        Map<Long, List<DocumentChunk>> chunksByDoc = chunks.stream()
                .collect(Collectors.groupingBy(DocumentChunk::getDocumentId));

        // 3. 构建论文信息文本
        String papersInfo = buildPapersInfo(papers, chunksByDoc);

        // 4. 使用模板构建 prompt
        Map<String, Object> variables = new HashMap<>();
        variables.put("topic", topic);
        variables.put("papers", papersInfo);

        String prompt = promptTemplateManager.render("literature-review-prompt", variables);

        // 5. 调用 LLM 生成文献综述
        ChatClient chatClient = resolveChatClient();
        if (chatClient == null) {
            log.error("No ChatClient available for literature review generation");
            return "无法生成文献综述：没有可用的 LLM 服务。";
        }

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                    chatClient.prompt()
                            .user(prompt)
                            .call()
                            .content()
            );

            String result = future.get(LLM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            log.info("Literature review generated successfully for topic '{}' in KB {}", topic, knowledgeBaseId);
            return result != null ? result.trim() : "文献综述生成失败，请重试。";

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Literature review generation timed out after {} ms", LLM_TIMEOUT_MS);
            return "文献综述生成超时，请缩小研究主题范围或减少知识库中的论文数量后重试。";
        } catch (Exception e) {
            log.error("Literature review generation failed", e);
            return "文献综述生成失败：" + e.getMessage();
        }
    }

    /**
     * 构建论文信息文本，包含元数据和内容摘要。
     */
    private String buildPapersInfo(List<Document> papers, Map<Long, List<DocumentChunk>> chunksByDoc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < papers.size(); i++) {
            Document doc = papers.get(i);
            sb.append("论文 ").append(i + 1).append(":\n");
            sb.append("  文件名: ").append(doc.getFileName() != null ? doc.getFileName() : "Unknown").append("\n");

            if (doc.getAuthors() != null) {
                sb.append("  作者: ").append(doc.getAuthors()).append("\n");
            }
            if (doc.getPublicationYear() != null) {
                sb.append("  年份: ").append(doc.getPublicationYear()).append("\n");
            }
            if (doc.getDoi() != null) {
                sb.append("  DOI: ").append(doc.getDoi()).append("\n");
            }
            if (doc.getJournal() != null) {
                sb.append("  期刊: ").append(doc.getJournal()).append("\n");
            }
            if (doc.getSummary() != null) {
                sb.append("  摘要: ").append(truncate(doc.getSummary(), 500)).append("\n");
            }

            // 添加内容片段作为上下文
            List<DocumentChunk> docChunks = chunksByDoc.getOrDefault(doc.getId(), Collections.emptyList());
            if (!docChunks.isEmpty()) {
                sb.append("  关键内容:\n");
                int limit = Math.min(docChunks.size(), MAX_CHUNKS_PER_DOC);
                for (int j = 0; j < limit; j++) {
                    String content = docChunks.get(j).getContent();
                    sb.append("    ").append(truncate(content, 300)).append("\n");
                }
            }

            sb.append("\n");
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * 解析可用的 ChatClient，优先使用 deepseek（成本低、速度快）。
     */
    private ChatClient resolveChatClient() {
        if (chatClients == null || chatClients.isEmpty()) return null;
        if (chatClients.containsKey("deepseek")) {
            return chatClients.get("deepseek");
        }
        return chatClients.values().iterator().next();
    }
}
