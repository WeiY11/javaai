package com.example.evimind.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.KgEntity;

@Mapper
public interface KgEntityMapper extends BaseMapper<KgEntity> {

  @Select(
      "SELECT * FROM kg_entity WHERE knowledge_base_id = #{knowledgeBaseId} ORDER BY created_at")
  List<KgEntity> findByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

  @Select("SELECT * FROM kg_entity WHERE document_id = #{documentId} ORDER BY created_at")
  List<KgEntity> findByDocumentId(@Param("documentId") Long documentId);

  @Select(
      "SELECT * FROM kg_entity WHERE name = #{name} AND knowledge_base_id = #{knowledgeBaseId} LIMIT 1")
  KgEntity findByNameAndKb(
      @Param("name") String name, @Param("knowledgeBaseId") Long knowledgeBaseId);

  @Select(
      """
        SELECT DISTINCT e.* FROM kg_entity e
        JOIN kg_relation r ON (r.source_entity_id = e.id OR r.target_entity_id = e.id)
        WHERE (r.source_entity_id = #{entityId} OR r.target_entity_id = #{entityId})
          AND e.id != #{entityId}
        ORDER BY e.name
        """)
  List<KgEntity> findNeighbors(@Param("entityId") Long entityId);
}
