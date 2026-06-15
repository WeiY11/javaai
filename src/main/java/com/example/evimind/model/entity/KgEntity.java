package com.example.evimind.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
