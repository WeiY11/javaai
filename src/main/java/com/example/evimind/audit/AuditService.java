package com.example.evimind.audit;

import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.AuditLogMapper;
import com.example.evimind.model.entity.AuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 操作审计日志服务。 记录用户的关键操作（文档上传/删除、知识库创建/删除、权限变更等）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditLogMapper auditLogMapper;
  private final ObjectMapper objectMapper;

  /** 同步记录审计日志。 */
  public void log(String action, String resourceType, Long resourceId) {
    log(action, resourceType, resourceId, null);
  }

  /** 同步记录审计日志（含详情）。 */
  public void log(String action, String resourceType, Long resourceId, Map<String, Object> detail) {
    try {
      AuditLog auditLog = new AuditLog();
      auditLog.setUserId(GroupContext.getUserId());
      auditLog.setAction(action);
      auditLog.setResourceType(resourceType);
      auditLog.setResourceId(resourceId);
      if (detail != null) {
        auditLog.setDetail(objectMapper.writeValueAsString(detail));
      } else {
        auditLog.setDetail("{}");
      }
      auditLogMapper.insert(auditLog);
    } catch (Exception e) {
      // 审计日志记录失败不应影响主流程
      log.warn(
          "Failed to write audit log: action={}, resource={}/{}, error={}",
          action,
          resourceType,
          resourceId,
          e.getMessage());
    }
  }

  /** 异步记录审计日志（用于非关键路径）。 */
  @Async
  public void logAsync(
      String action, String resourceType, Long resourceId, Map<String, Object> detail) {
    log(action, resourceType, resourceId, detail);
  }

  /** 分页查询审计日志。 */
  public IPage<AuditLog> queryLogs(
      int page, int size, Long userId, String action, String resourceType) {
    LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
    if (userId != null) {
      wrapper.eq(AuditLog::getUserId, userId);
    }
    if (action != null && !action.isBlank()) {
      wrapper.eq(AuditLog::getAction, action);
    }
    if (resourceType != null && !resourceType.isBlank()) {
      wrapper.eq(AuditLog::getResourceType, resourceType);
    }
    wrapper.orderByDesc(AuditLog::getCreatedAt);
    return auditLogMapper.selectPage(new Page<>(page, size), wrapper);
  }
}
