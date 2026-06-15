package com.example.evimind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.KgRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KgRelationMapper extends BaseMapper<KgRelation> {

    @Select("SELECT * FROM kg_relation WHERE knowledge_base_id = #{knowledgeBaseId} ORDER BY created_at")
    List<KgRelation> findByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("SELECT * FROM kg_relation WHERE document_id = #{documentId} ORDER BY created_at")
    List<KgRelation> findByDocumentId(@Param("documentId") Long documentId);

    @Select("SELECT * FROM kg_relation WHERE source_entity_id = #{entityId} OR target_entity_id = #{entityId}")
    List<KgRelation> findByEntityId(@Param("entityId") Long entityId);

    @Select("""
        SELECT * FROM kg_relation
        WHERE source_entity_id = #{sourceId} AND target_entity_id = #{targetId}
        LIMIT 1
        """)
    KgRelation findDirectRelation(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
