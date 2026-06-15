package com.example.evimind.scheduler;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.ingestion.EtlPipeline;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.ScheduledTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 知识库重索引任务执行器。 重新触发知识库中所有文档的 ETL 流程。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbReindexExecutor implements TaskExecutor {

  private final DocumentMapper documentMapper;
  private final EtlPipeline etlPipeline;
  private final ObjectMapper objectMapper;

  @Override
  public String getTaskType() {
    return "KB_REINDEX";
  }

  @Override
  public void execute(ScheduledTask task) {
    try {
      Map<String, Object> config =
          objectMapper.readValue(task.getConfig(), new TypeReference<>() {});
      Long knowledgeBaseId =
          config.get("knowledgeBaseId") != null
              ? Long.valueOf(config.get("knowledgeBaseId").toString())
              : null;

      if (knowledgeBaseId == null) {
        log.warn("KB_REINDEX task {} missing knowledgeBaseId config", task.getId());
        return;
      }

      List<Document> docs =
          documentMapper.selectList(
              new LambdaQueryWrapper<Document>()
                  .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                  .eq(Document::getIngestionStatus, "COMPLETED"));

      log.info("KB_REINDEX: re-indexing {} documents in KB {}", docs.size(), knowledgeBaseId);
      for (Document doc : docs) {
        try {
          doc.setIngestionStatus("PENDING");
          documentMapper.updateById(doc);
          etlPipeline.processDocument(doc.getId());
        } catch (Exception e) {
          log.warn("KB_REINDEX: failed to re-index document {}: {}", doc.getId(), e.getMessage());
        }
      }
    } catch (Exception e) {
      log.error("KB_REINDEX task {} failed: {}", task.getId(), e.getMessage());
    }
  }
}
