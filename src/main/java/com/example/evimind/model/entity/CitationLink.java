package com.example.evimind.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("citation_link")
public class CitationLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long knowledgeBaseId;

    private String citedDoi;

    private String citedTitle;

    private String citedAuthors;

    private Integer citedYear;

    private String rawReference;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
