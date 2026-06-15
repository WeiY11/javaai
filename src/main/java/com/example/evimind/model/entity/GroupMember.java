package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

@Data
@TableName("group_member")
public class GroupMember {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long groupId;

  private Long userId;

  private String role;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime joinedAt;
}
