package com.example.evimind.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    private String citations;

    private String toolCalls;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
