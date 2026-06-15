package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

@Data
@TableName("refresh_token")
public class RefreshToken {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  private String tokenHash;

  private LocalDateTime expiresAt;

  private Boolean revoked;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;
}
