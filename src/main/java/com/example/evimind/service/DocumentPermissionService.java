package com.example.evimind.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.DocumentPermissionMapper;
import com.example.evimind.model.entity.DocumentPermission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档级权限控制服务。 支持 READ / WRITE / ADMIN 三级权限，文档上传时自动给上传者授予 ADMIN 权限。 知识库成员默认拥有 READ 权限，文档级权限可以进一步细化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPermissionService {

  private final DocumentPermissionMapper permissionMapper;
  private final DocumentMapper documentMapper;

  public static final String PERM_READ = "READ";
  public static final String PERM_WRITE = "WRITE";
  public static final String PERM_ADMIN = "ADMIN";

  /** 文档上传后调用：给上传者授予 ADMIN 权限。 */
  @Transactional
  public void grantOwnerPermission(Long documentId) {
    Long userId = GroupContext.getUserId();
    if (userId == null) return;

    grantPermission(documentId, userId, PERM_ADMIN, userId);
  }

  /** 授予用户对指定文档的权限。 */
  @Transactional
  public void grantPermission(Long documentId, Long userId, String permissionType, Long grantedBy) {
    // 检查是否已有该权限
    List<DocumentPermission> existing = permissionMapper.findByDocumentAndUser(documentId, userId);
    boolean alreadyHas =
        existing.stream().anyMatch(p -> p.getPermissionType().equals(permissionType));
    if (alreadyHas) return;

    DocumentPermission perm = new DocumentPermission();
    perm.setDocumentId(documentId);
    perm.setUserId(userId);
    perm.setPermissionType(permissionType);
    perm.setGrantedBy(grantedBy);
    permissionMapper.insert(perm);
    log.info("Granted {} permission on document {} to user {}", permissionType, documentId, userId);
  }

  /** 撤销用户对指定文档的权限。 */
  @Transactional
  public void revokePermission(Long documentId, Long userId, String permissionType) {
    permissionMapper.delete(
        new LambdaQueryWrapper<DocumentPermission>()
            .eq(DocumentPermission::getDocumentId, documentId)
            .eq(DocumentPermission::getUserId, userId)
            .eq(DocumentPermission::getPermissionType, permissionType));
    log.info(
        "Revoked {} permission on document {} from user {}", permissionType, documentId, userId);
  }

  /** 检查用户是否有指定文档的指定权限。 */
  public boolean hasPermission(Long documentId, Long userId, String permissionType) {
    if (userId == null) return false;

    List<DocumentPermission> perms = permissionMapper.findByDocumentAndUser(documentId, userId);
    Set<String> permTypes =
        perms.stream().map(DocumentPermission::getPermissionType).collect(Collectors.toSet());

    // ADMIN 权限包含所有低级别权限
    if (permTypes.contains(PERM_ADMIN)) return true;
    if (PERM_WRITE.equals(permissionType) && permTypes.contains(PERM_ADMIN)) return true;
    return permTypes.contains(permissionType);
  }

  /** 要求用户对文档有指定权限，否则抛出异常。 */
  public void requirePermission(Long documentId, Long userId, String permissionType) {
    if (!hasPermission(documentId, userId, permissionType)) {
      throw new SecurityException("无权访问该文档（需要 " + permissionType + " 权限）");
    }
  }

  /** 获取文档的所有权限列表。 */
  public List<Map<String, Object>> getDocumentPermissions(Long documentId) {
    List<DocumentPermission> perms = permissionMapper.findByDocumentId(documentId);
    return perms.stream()
        .map(
            p -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("id", p.getId());
              item.put("userId", p.getUserId());
              item.put("permissionType", p.getPermissionType());
              item.put("grantedBy", p.getGrantedBy());
              item.put("createdAt", p.getCreatedAt());
              return item;
            })
        .collect(Collectors.toList());
  }

  /** 获取用户有 READ 权限的文档 ID 集合。 用于检索时过滤。 */
  public Set<Long> getReadableDocumentIds(Long userId) {
    List<Long> ids = permissionMapper.findPermittedDocumentIds(userId, PERM_READ);
    return new HashSet<>(ids);
  }

  /** 获取用户有 ADMIN 权限的文档 ID 集合。 */
  public Set<Long> getAdminDocumentIds(Long userId) {
    List<Long> ids = permissionMapper.findPermittedDocumentIds(userId, PERM_ADMIN);
    return new HashSet<>(ids);
  }

  /** 检查文档是否设置了权限（如果没有，则所有 KB 成员都可以访问）。 */
  public boolean hasRestrictions(Long documentId) {
    return permissionMapper.findByDocumentId(documentId).size() > 0;
  }
}
