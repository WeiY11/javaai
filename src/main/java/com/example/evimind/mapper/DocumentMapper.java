package com.example.evimind.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.evimind.model.entity.Document;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

  @Select(
      """
      SELECT d.*
      FROM document d
      WHERE d.knowledge_base_id = #{knowledgeBaseId}
        AND (
          NOT EXISTS (
            SELECT 1 FROM document_permission dp
            WHERE dp.document_id = d.id
          )
          OR EXISTS (
            SELECT 1 FROM document_permission dp
            WHERE dp.document_id = d.id
              AND dp.user_id = #{userId}
              AND dp.permission_type IN ('READ', 'ADMIN')
          )
        )
      ORDER BY d.created_at DESC
      """)
  IPage<Document> selectAccessiblePage(
      IPage<Document> page,
      @Param("knowledgeBaseId") Long knowledgeBaseId,
      @Param("userId") Long userId);

  @Select(
      """
      <script>
      SELECT d.id
      FROM document d
      WHERE d.id IN
      <foreach collection="documentIds" item="documentId" open="(" separator="," close=")">
        #{documentId}
      </foreach>
        AND d.knowledge_base_id = #{knowledgeBaseId}
        AND (
          NOT EXISTS (
            SELECT 1 FROM document_permission dp
            WHERE dp.document_id = d.id
          )
          OR EXISTS (
            SELECT 1 FROM document_permission dp
            WHERE dp.document_id = d.id
              AND dp.user_id = #{userId}
              AND dp.permission_type IN ('READ', 'ADMIN')
          )
        )
      </script>
      """)
  List<Long> findReadableDocumentIds(
      @Param("knowledgeBaseId") Long knowledgeBaseId,
      @Param("documentIds") Collection<Long> documentIds,
      @Param("userId") Long userId);

  @Update(
      """
      UPDATE document
      SET ingestion_status = 'EXTRACTING',
          ingestion_version = COALESCE(ingestion_version, 0) + 1,
          started_at = CURRENT_TIMESTAMP,
          finished_at = NULL,
          failed_stage = NULL,
          error_code = NULL,
          error_message = NULL,
          updated_at = CURRENT_TIMESTAMP
      WHERE id = #{documentId}
        AND ingestion_status NOT IN ('EXTRACTING','CLEANING','CHUNKING','PERSISTING','EMBEDDING','INDEXING','ENRICHING')
      """)
  int claimIngestion(@Param("documentId") Long documentId);
}
