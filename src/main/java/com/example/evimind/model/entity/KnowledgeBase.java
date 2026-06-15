package com.example.evimind.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

@Data
@TableName("knowledge_base")
public class KnowledgeBase {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;

  private String description;

  private Long groupId;

  private BigDecimal evidenceThreshold;

  private String chunkStrategy;

  private Integer chunkSize;

  private Integer chunkOverlap;

  private String status;

  private Long creatorId;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;
}
