package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("audit_log")
public class AuditLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private String action;
  private String resourceType;
  private Long resourceId;
  private String detail;
  private String ipAddress;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;
}
