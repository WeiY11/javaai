package com.example.evimind.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要审计日志的方法。
 * 通过 AOP 拦截，自动记录操作到 audit_log 表。
 *
 * 用法：
 * @Auditable(action = "DOCUMENT_UPLOAD", resourceType = "DOCUMENT")
 * public void uploadDocument(...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 操作类型，例如：DOCUMENT_UPLOAD, DOCUMENT_DELETE, KB_CREATE, KB_DELETE, PERMISSION_CHANGE
     */
    String action();

    /**
     * 资源类型，例如：DOCUMENT, KNOWLEDGE_BASE, USER, PERMISSION
     */
    String resourceType();

    /**
     * SpEL 表达式，用于从方法参数中提取资源 ID。
     * 例如："#documentId" 或 "#args[0]"
     */
    String resourceIdExpression() default "";
}
