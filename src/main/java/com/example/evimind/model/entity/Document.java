package com.example.evimind.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeBaseId;

    private String fileName;

    private String fileFormat;

    private Long fileSize;

    private String storagePath;

    private String ingestionStatus;

    private Integer chunkCount;

    private Long uploaderId;

    private String summary;

    private String doi;
    private String authors;
    private Integer publicationYear;
    private String journal;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
