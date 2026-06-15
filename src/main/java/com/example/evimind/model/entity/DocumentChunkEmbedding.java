package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

@Data
@TableName("document_chunk_embedding")
public class DocumentChunkEmbedding {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long chunkId;

  private Long knowledgeBaseId;

  private String embedding;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;
}
