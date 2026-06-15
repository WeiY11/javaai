package com.example.evimind.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.CitationLink;

@Mapper
public interface CitationLinkMapper extends BaseMapper<CitationLink> {

  @Select("SELECT * FROM citation_link WHERE document_id = #{documentId} ORDER BY created_at DESC")
  List<CitationLink> findByDocumentId(@Param("documentId") Long documentId);

  @Select(
      "SELECT * FROM citation_link WHERE knowledge_base_id = #{knowledgeBaseId} ORDER BY created_at DESC")
  List<CitationLink> findByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

  @Select("SELECT * FROM citation_link WHERE cited_doi = #{doi} ORDER BY created_at DESC")
  List<CitationLink> findByCitedDoi(@Param("doi") String doi);

  /** 查找与给定文档共同引用的文献（共被引分析）。 逻辑：找出给定文档引用的 DOI，然后找出在同一知识库中也引用了这些 DOI 的其他文档。 */
  @Select(
      """
        SELECT DISTINCT cl2.*
        FROM citation_link cl1
        JOIN citation_link cl2
            ON cl1.cited_doi = cl2.cited_doi
            AND cl2.knowledge_base_id = cl1.knowledge_base_id
            AND cl2.document_id != cl1.document_id
        WHERE cl1.document_id = #{documentId}
          AND cl1.knowledge_base_id = #{knowledgeBaseId}
          AND cl1.cited_doi IS NOT NULL
          AND cl2.cited_doi IS NOT NULL
        ORDER BY cl2.cited_doi
        """)
  List<CitationLink> findCoCitations(
      @Param("documentId") Long documentId, @Param("knowledgeBaseId") Long knowledgeBaseId);
}
