package com.example.evimind.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("document_chunk")
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long knowledgeBaseId;

    private String content;

    private Integer chunkIndex;

    private String vectorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
