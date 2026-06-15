package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

@Data
@TableName("sys_group")
public class Group {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;

  private String description;

  private String orgCode;

  private Long creatorId;

  private String status;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;
}
