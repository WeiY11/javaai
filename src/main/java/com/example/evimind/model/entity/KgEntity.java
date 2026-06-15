package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("kg_entity")
public class KgEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String entityType;
  private String description;
  private Long knowledgeBaseId;
  private Long documentId;
  private String embedding;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;
}
