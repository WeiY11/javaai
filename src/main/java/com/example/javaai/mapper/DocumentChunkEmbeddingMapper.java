package com.example.javaai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaai.model.entity.DocumentChunkEmbedding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkEmbeddingMapper extends BaseMapper<DocumentChunkEmbedding> {

    @Select("SELECT dce.chunk_id, dce.knowledge_base_id, dc.document_id, dc.content, dc.chunk_index, " +
            "1 - (dce.embedding <=> #{queryEmbedding}::vector) AS score " +
            "FROM document_chunk_embedding dce " +
            "JOIN document_chunk dc ON dc.id = dce.chunk_id " +
            "WHERE dce.knowledge_base_id = #{knowledgeBaseId} " +
            "ORDER BY dce.embedding <=> #{queryEmbedding}::vector " +
            "LIMIT #{limit}")
    List<ChunkSimilarityResult> findSimilarChunks(@Param("queryEmbedding") String queryEmbedding,
                                                   @Param("knowledgeBaseId") Long knowledgeBaseId,
                                                   @Param("limit") int limit);

    @org.apache.ibatis.annotations.Delete("DELETE FROM document_chunk_embedding WHERE chunk_id IN " +
            "(SELECT id FROM document_chunk WHERE document_id = #{documentId})")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM document_chunk_embedding WHERE knowledge_base_id = #{kbId}")
    void deleteByKnowledgeBaseId(@Param("kbId") Long knowledgeBaseId);

    interface ChunkSimilarityResult {
        Long getChunkId();
        Long getKnowledgeBaseId();
        Long getDocumentId();
        String getContent();
        Integer getChunkIndex();
        Double getScore();
    }
}
