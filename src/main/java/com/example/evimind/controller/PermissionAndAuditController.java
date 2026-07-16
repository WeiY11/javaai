package com.example.evimind.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.evimind.audit.AuditService;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.model.entity.AuditLog;
import com.example.evimind.service.DocumentPermissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PermissionAndAuditController {

  private final DocumentPermissionService documentPermissionService;
  private final AuditService auditService;

  /** 获取文档的权限列表 */
  @GetMapping("/api/v1/documents/{docId}/permissions")
  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPermissions(
      @PathVariable Long docId) {
    Long currentUserId = GroupContext.getUserId();
    documentPermissionService.requirePermission(
        docId, currentUserId, DocumentPermissionService.PERM_ADMIN);
    List<Map<String, Object>> permissions = documentPermissionService.getDocumentPermissions(docId);
    return ResponseEntity.ok(ApiResponse.success(permissions));
  }

  /** 授予用户对文档的权限 */
  @PostMapping("/api/v1/documents/{docId}/permissions")
  public ResponseEntity<ApiResponse<Void>> grantPermission(
      @PathVariable Long docId, @RequestBody Map<String, Object> body) {
    Long userId = Long.valueOf(body.get("userId").toString());
    String permissionType = (String) body.get("permissionType");

    Long currentUserId = GroupContext.getUserId();
    documentPermissionService.requirePermission(
        docId, currentUserId, DocumentPermissionService.PERM_ADMIN);
    documentPermissionService.grantPermission(docId, userId, permissionType, currentUserId);

    auditService.log(
        "PERMISSION_GRANT",
        "DOCUMENT",
        docId,
        Map.of("targetUserId", userId, "permissionType", permissionType));

    return ResponseEntity.ok(ApiResponse.success(null));
  }

  /** 撤销用户对文档的权限 */
  @DeleteMapping("/api/v1/documents/{docId}/permissions/{userId}")
  public ResponseEntity<ApiResponse<Void>> revokePermission(
      @PathVariable Long docId, @PathVariable Long userId, @RequestParam String permissionType) {
    Long currentUserId = GroupContext.getUserId();
    documentPermissionService.requirePermission(
        docId, currentUserId, DocumentPermissionService.PERM_ADMIN);
    documentPermissionService.revokePermission(docId, userId, permissionType);

    auditService.log(
        "PERMISSION_REVOKE",
        "DOCUMENT",
        docId,
        Map.of("targetUserId", userId, "permissionType", permissionType));

    return ResponseEntity.ok(ApiResponse.success(null));
  }

  /** 查询审计日志（管理员接口） */
  @GetMapping("/api/v1/admin/audit-logs")
  public ResponseEntity<ApiResponse<IPage<AuditLog>>> queryAuditLogs(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String resourceType) {
    IPage<AuditLog> logs = auditService.queryLogs(page, size, userId, action, resourceType);
    return ResponseEntity.ok(ApiResponse.success(logs));
  }
}
