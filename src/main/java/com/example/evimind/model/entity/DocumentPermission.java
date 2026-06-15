package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("document_permission")
public class DocumentPermission {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long documentId;
  private Long userId;
  private String permissionType;
  private Long grantedBy;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;
}
