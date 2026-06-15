package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

@Data
@TableName("kb_member")
public class KbMember {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long knowledgeBaseId;

  private Long userId;

  private String role;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime joinedAt;
}
