package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

@Data
@TableName("message")
public class Message {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long conversationId;

  private String role;

  private String content;

  private String citations;

  private String toolCalls;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;
}
