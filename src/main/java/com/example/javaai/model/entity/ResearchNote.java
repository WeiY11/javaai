package com.example.javaai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("research_note")
public class ResearchNote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chunkId;
    private Long documentId;
    private Long knowledgeBaseId;
    private Long userId;
    private String content;
    private String highlight;
    private Integer startOffset;
    private Integer endOffset;
    private String tags;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
