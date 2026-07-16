package com.example.evimind.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.DocumentChunk;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

  @Select(
      """
      <script>
      SELECT dc.*
      FROM document_chunk dc
      JOIN document d
        ON d.id = dc.document_id
       AND d.active_ingestion_version = dc.ingestion_version
      WHERE dc.knowledge_base_id = #{knowledgeBaseId}
        AND (
          <foreach collection="terms" item="term" separator=" OR ">
            LOWER(dc.content) LIKE CONCAT(
              '%',
              REPLACE(REPLACE(REPLACE(#{term}, '!', '!!'), '%', '!%'), '_', '!_'),
              '%'
            ) ESCAPE '!'
          </foreach>
        )
      </script>
      """)
  List<DocumentChunk> findActiveContainingAnyTerm(
      @Param("knowledgeBaseId") Long knowledgeBaseId,
      @Param("terms") List<String> terms);

  @Delete(
      """
      DELETE FROM document_chunk
      WHERE document_id = #{documentId}
        AND ingestion_version <> #{activeVersion}
      """)
  int deleteInactiveVersions(
      @Param("documentId") Long documentId, @Param("activeVersion") Integer activeVersion);
}
