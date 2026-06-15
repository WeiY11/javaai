package com.example.evimind.model.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("scheduled_task")
public class ScheduledTask {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String cronExpression;
  private String taskType;
  private String config;
  private LocalDateTime lastRunAt;
  private LocalDateTime nextRunAt;
  private String status;
  private Long creatorId;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;
}
