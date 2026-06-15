package com.example.evimind.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.mapper.KgEntityMapper;
import com.example.evimind.mapper.KgRelationMapper;
import com.example.evimind.model.entity.KgEntity;
import com.example.evimind.model.entity.KgRelation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 LLM 的实体-关系抽取器。
 * 从文档文本中提取 (实体, 关系, 实体) 三元组，构建知识图谱。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityRelationExtractor {

    private final KgEntityMapper entityMapper;
    private final KgRelationMapper relationMapper;
    private final Map<String, ChatClient> chatClients;
    private final ObjectMapper objectMapper;

    @Value("${custom.kg.enabled:true}")
    private boolean kgEnabled;

    @Value("${custom.kg.extraction.timeout-ms:60000}")
    private long extractionTimeoutMs;

    @Value("${custom.kg.extraction.max-text-chars:4000}")
    private int maxTextChars;

    @Value("${custom.kg.extraction.max-entities:50}")
    private int maxEntities;

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[[\\s\\S]*\\]");

    /**
     * 从文档文本中抽取实体和关系并持久化。
     *
     * @param documentId      文档 ID
     * @param knowledgeBaseId 知识库 ID
     * @param text            文档全文（或已清洗文本）
     * @return 抽取的三元组数量
     */
    @Transactional
    public int extractAndSave(Long documentId, Long knowledgeBaseId, String text) {
        if (!kgEnabled || text == null || text.isBlank()) {
            return 0;
        }

        // 先清理该文档已有的知识图谱数据，避免重复
        cleanExisting(documentId);

        String truncated = text.length() > maxTextChars
                ? text.substring(0, maxTextChars)
                : text;

        String prompt = buildPrompt(truncated);
        ChatClient chatClient = resolveChatClient();
        if (chatClient == null) {
            log.warn("No ChatClient available for entity-relation extraction");
            return 0;
        }

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                    chatClient.prompt()
                            .system("你是一个专业的知识图谱构建助手。请从文本中提取实体和关系，严格按照指定的 JSON 格式输出。")
                            .user(prompt)
                            .call()
                            .content()
            );

            String response = future.get(extractionTimeoutMs, TimeUnit.MILLISECONDS);
            if (response == null || response.isBlank()) {
                log.warn("Empty response from LLM for entity-relation extraction, document {}", documentId);
                return 0;
            }

            return parseAndSave(response, documentId, knowledgeBaseId);

        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Entity-relation extraction timed out for document {}", documentId);
            return 0;
        } catch (Exception e) {
            log.warn("Entity-relation extraction failed for document {}: {}", documentId, e.getMessage());
            return 0;
        }
    }

    /**
     * 删除指定文档的所有知识图谱数据。
     */
    @Transactional
    public void cleanExisting(Long documentId) {
        // 先删除关系（外键约束），再删除实体
        List<KgRelation> existingRelations = relationMapper.findByDocumentId(documentId);
        for (KgRelation rel : existingRelations) {
            relationMapper.deleteById(rel.getId());
        }
        List<KgEntity> existingEntities = entityMapper.findByDocumentId(documentId);
        for (KgEntity entity : existingEntities) {
            entityMapper.deleteById(entity.getId());
        }
    }

    private String buildPrompt(String text) {
        return """
            请从以下文本中提取实体和关系，构建知识图谱三元组。
            
            要求：
            1. 提取重要实体（人物、组织、概念、技术、地点、事件等）
            2. 提取实体之间的关系（如：属于、创建、使用、位于、导致、解决等）
            3. 每个实体需要有名称和类型
            4. 关系需要有明确的类型描述
            5. 最多提取 %d 个实体
            
            输出格式（严格 JSON）：
            {
              "entities": [
                {"name": "实体名", "type": "实体类型", "description": "简短描述"}
              ],
              "relations": [
                {"source": "源实体名", "target": "目标实体名", "relation": "关系类型"}
              ]
            }
            
            文本内容：
            %s
            """.formatted(maxEntities, text);
    }

    @SuppressWarnings("unchecked")
    private int parseAndSave(String response, Long documentId, Long knowledgeBaseId) {
        // 提取 JSON 块
        String json = extractJson(response);
        if (json == null) {
            log.warn("Could not find valid JSON in entity-relation extraction response");
            return 0;
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});

            List<Map<String, String>> entitiesData = (List<Map<String, String>>) parsed.get("entities");
            List<Map<String, String>> relationsData = (List<Map<String, String>>) parsed.get("relations");

            if (entitiesData == null || entitiesData.isEmpty()) {
                log.info("No entities extracted for document {}", documentId);
                return 0;
            }

            // 插入实体，建立 name → id 映射
            Map<String, Long> entityIdMap = new LinkedHashMap<>();
            for (Map<String, String> ed : entitiesData) {
                String name = ed.get("name");
                if (name == null || name.isBlank()) continue;

                // 检查知识库中是否已存在同名实体（跨文档复用）
                KgEntity existing = entityMapper.findByNameAndKb(name, knowledgeBaseId);
                if (existing != null) {
                    entityIdMap.put(name, existing.getId());
                    continue;
                }

                KgEntity entity = new KgEntity();
                entity.setName(name);
                entity.setEntityType(ed.getOrDefault("type", "unknown"));
                entity.setDescription(ed.get("description"));
                entity.setKnowledgeBaseId(knowledgeBaseId);
                entity.setDocumentId(documentId);
                entityMapper.insert(entity);
                entityIdMap.put(name, entity.getId());
            }

            // 插入关系
            int relationCount = 0;
            if (relationsData != null) {
                for (Map<String, String> rd : relationsData) {
                    String sourceName = rd.get("source");
                    String targetName = rd.get("target");
                    String relationType = rd.get("relation");

                    Long sourceId = entityIdMap.get(sourceName);
                    Long targetId = entityIdMap.get(targetName);

                    if (sourceId == null || targetId == null || relationType == null) continue;

                    KgRelation relation = new KgRelation();
                    relation.setSourceEntityId(sourceId);
                    relation.setTargetEntityId(targetId);
                    relation.setRelationType(relationType);
                    relation.setDocumentId(documentId);
                    relation.setKnowledgeBaseId(knowledgeBaseId);
                    relation.setProperties("{}");
                    relationMapper.insert(relation);
                    relationCount++;
                }
            }

            log.info("Extracted {} entities and {} relations from document {}",
                    entityIdMap.size(), relationCount, documentId);
            return relationCount;

        } catch (Exception e) {
            log.warn("Failed to parse entity-relation extraction response for document {}: {}",
                    documentId, e.getMessage());
            return 0;
        }
    }

    private String extractJson(String response) {
        // 尝试提取 JSON 对象
        Matcher objMatcher = JSON_BLOCK_PATTERN.matcher(response);
        if (objMatcher.find()) {
            return objMatcher.group();
        }
        // 尝试提取 JSON 数组
        Matcher arrMatcher = JSON_ARRAY_PATTERN.matcher(response);
        if (arrMatcher.find()) {
            return arrMatcher.group();
        }
        return null;
    }

    private ChatClient resolveChatClient() {
        if (chatClients == null || chatClients.isEmpty()) return null;
        if (chatClients.containsKey("deepseek")) return chatClients.get("deepseek");
        return chatClients.values().iterator().next();
    }
}
