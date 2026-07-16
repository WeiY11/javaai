package com.example.evimind.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.evimind.audit.AuditService;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.service.DocumentPermissionService;

class PermissionAndAuditControllerTest {

  private final DocumentPermissionService documentPermissionService =
      mock(DocumentPermissionService.class);
  private final PermissionAndAuditController controller =
      new PermissionAndAuditController(documentPermissionService, mock(AuditService.class));

  @BeforeEach
  void setUp() {
    GroupContext.set(1L, 1L, "USER");
  }

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void getPermissionsShouldRequireDocumentAdminPermission() {
    controller.getPermissions(42L);

    org.mockito.InOrder permissionOrder = inOrder(documentPermissionService);
    permissionOrder
        .verify(documentPermissionService)
        .requirePermission(42L, 1L, DocumentPermissionService.PERM_ADMIN);
    permissionOrder.verify(documentPermissionService).getDocumentPermissions(42L);
  }
}
