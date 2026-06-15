package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("kg_relation")
public class KgRelation {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long sourceEntityId;
  private Long targetEntityId;
  private String relationType;
  private String properties;
  private Long documentId;
  private Long knowledgeBaseId;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;
}
