package com.example.evimind.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.evimind.identity.GroupContext;
import com.example.evimind.knowledgebase.KnowledgeBaseService;
import com.example.evimind.mapper.KbMemberMapper;
import com.example.evimind.mapper.KgEntityMapper;
import com.example.evimind.mapper.KgRelationMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.KgEntity;
import com.example.evimind.model.entity.KgRelation;
import com.example.evimind.model.entity.KnowledgeBase;

class KnowledgeGraphServiceTest {

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void getGraphShouldRejectUsersWhoAreNotKnowledgeBaseMembers() {
    KgEntityMapper entityMapper = mock(KgEntityMapper.class);
    KgRelationMapper relationMapper = mock(KgRelationMapper.class);
    KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    KbMemberMapper kbMemberMapper = mock(KbMemberMapper.class);
    KnowledgeBase knowledgeBase = new KnowledgeBase();
    knowledgeBase.setId(9L);
    GroupContext.set(7L, 1L, "USER");
    when(knowledgeBaseMapper.selectById(9L)).thenReturn(knowledgeBase);
    when(kbMemberMapper.selectCount(any())).thenReturn(0L);

    KnowledgeGraphService service =
        createService(
            entityMapper,
            relationMapper,
            new KnowledgeBaseService(knowledgeBaseMapper, kbMemberMapper));

    assertThatThrownBy(() -> service.getGraph(9L))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("not a member");

    verifyNoInteractions(entityMapper, relationMapper);
  }

  @Test
  void findPathShouldRejectEntitiesOutsideTheRequestedKnowledgeBase() {
    KgEntityMapper entityMapper = mock(KgEntityMapper.class);
    KgRelationMapper relationMapper = mock(KgRelationMapper.class);
    KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    KbMemberMapper kbMemberMapper = mock(KbMemberMapper.class);
    KnowledgeBase knowledgeBase = new KnowledgeBase();
    knowledgeBase.setId(9L);
    KgEntity foreignSource = new KgEntity();
    foreignSource.setId(1L);
    foreignSource.setKnowledgeBaseId(10L);
    KgEntity target = new KgEntity();
    target.setId(2L);
    target.setKnowledgeBaseId(9L);
    GroupContext.set(7L, 1L, "USER");
    when(knowledgeBaseMapper.selectById(9L)).thenReturn(knowledgeBase);
    when(kbMemberMapper.selectCount(any())).thenReturn(1L);
    when(entityMapper.selectById(1L)).thenReturn(foreignSource);
    when(entityMapper.selectById(2L)).thenReturn(target);

    KnowledgeGraphService service =
        createService(
            entityMapper,
            relationMapper,
            new KnowledgeBaseService(knowledgeBaseMapper, kbMemberMapper));

    assertThatThrownBy(() -> findPath(service, 9L, 1L, 2L, 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found in knowledge base");

    verifyNoInteractions(relationMapper);
  }

  @Test
  @SuppressWarnings("unchecked")
  void getGraphShouldExcludeRestrictedDocumentEntitiesAndRelations() {
    KgEntityMapper entityMapper = mock(KgEntityMapper.class);
    KgRelationMapper relationMapper = mock(KgRelationMapper.class);
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    DocumentPermissionService documentPermissionService = mock(DocumentPermissionService.class);
    KnowledgeBase knowledgeBase = new KnowledgeBase();
    knowledgeBase.setId(9L);
    KgEntity accessibleSource = entity(1L, 9L, 101L);
    KgEntity restrictedEntity = entity(2L, 9L, 102L);
    KgEntity accessibleTarget = entity(3L, 9L, 103L);
    KgRelation relationToRestricted = relation(11L, 9L, 101L, 1L, 2L);
    KgRelation visibleRelation = relation(12L, 9L, 103L, 1L, 3L);
    GroupContext.set(7L, 1L, "USER");
    when(knowledgeBaseService.getById(9L)).thenReturn(knowledgeBase);
    when(entityMapper.findByKnowledgeBaseId(9L))
        .thenReturn(List.of(accessibleSource, restrictedEntity, accessibleTarget));
    when(relationMapper.findByKnowledgeBaseId(9L))
        .thenReturn(List.of(relationToRestricted, visibleRelation));
    when(documentPermissionService.findReadableDocumentIds(9L, Set.of(101L, 102L, 103L), 7L))
        .thenReturn(Set.of(101L, 103L));
    when(documentPermissionService.findReadableDocumentIds(9L, Set.of(101L, 103L), 7L))
        .thenReturn(Set.of(101L, 103L));

    Map<String, Object> graph =
        createService(entityMapper, relationMapper, knowledgeBaseService, documentPermissionService)
            .getGraph(9L);

    List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
    List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
    assertThat(nodes).extracting(node -> node.get("id")).containsExactlyInAnyOrder(1L, 3L);
    assertThat(edges).extracting(edge -> edge.get("id")).containsExactly(12L);
  }

  @Test
  void getNeighborsShouldRejectARestrictedRootEntity() {
    KgEntityMapper entityMapper = mock(KgEntityMapper.class);
    KgRelationMapper relationMapper = mock(KgRelationMapper.class);
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    DocumentPermissionService documentPermissionService = mock(DocumentPermissionService.class);
    KnowledgeBase knowledgeBase = new KnowledgeBase();
    knowledgeBase.setId(9L);
    KgEntity restrictedEntity = entity(1L, 9L, 102L);
    GroupContext.set(7L, 1L, "USER");
    when(knowledgeBaseService.getById(9L)).thenReturn(knowledgeBase);
    when(entityMapper.selectById(1L)).thenReturn(restrictedEntity);
    when(documentPermissionService.findReadableDocumentIds(9L, Set.of(102L), 7L))
        .thenReturn(Set.of());

    KnowledgeGraphService service =
        createService(entityMapper, relationMapper, knowledgeBaseService, documentPermissionService);

    assertThatThrownBy(() -> service.getNeighbors(9L, 1L))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("restricted document");
    verifyNoInteractions(relationMapper);
  }

  @Test
  void findPathShouldNotTraverseRestrictedIntermediateEntities() {
    KgEntityMapper entityMapper = mock(KgEntityMapper.class);
    KgRelationMapper relationMapper = mock(KgRelationMapper.class);
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    DocumentPermissionService documentPermissionService = mock(DocumentPermissionService.class);
    KnowledgeBase knowledgeBase = new KnowledgeBase();
    knowledgeBase.setId(9L);
    KgEntity source = entity(1L, 9L, 101L);
    KgEntity restrictedIntermediate = entity(2L, 9L, 102L);
    KgEntity target = entity(3L, 9L, 103L);
    KgRelation relation = relation(11L, 9L, 101L, 1L, 2L);
    GroupContext.set(7L, 1L, "USER");
    when(knowledgeBaseService.getById(9L)).thenReturn(knowledgeBase);
    when(entityMapper.selectById(1L)).thenReturn(source);
    when(entityMapper.selectById(2L)).thenReturn(restrictedIntermediate);
    when(entityMapper.selectById(3L)).thenReturn(target);
    when(relationMapper.findByEntityId(1L)).thenReturn(List.of(relation));
    when(documentPermissionService.findReadableDocumentIds(9L, Set.of(101L), 7L))
        .thenReturn(Set.of(101L));
    when(documentPermissionService.findReadableDocumentIds(9L, Set.of(102L), 7L))
        .thenReturn(Set.of());
    when(documentPermissionService.findReadableDocumentIds(9L, Set.of(103L), 7L))
        .thenReturn(Set.of(103L));

    KnowledgeGraphService service =
        createService(entityMapper, relationMapper, knowledgeBaseService, documentPermissionService);

    assertThat(findPath(service, 9L, 1L, 3L, 3)).isEqualTo(List.of());
  }

  private KnowledgeGraphService createService(
      KgEntityMapper entityMapper,
      KgRelationMapper relationMapper,
      KnowledgeBaseService knowledgeBaseService) {
    return createService(entityMapper, relationMapper, knowledgeBaseService, mock(DocumentPermissionService.class));
  }

  private KnowledgeGraphService createService(
      KgEntityMapper entityMapper,
      KgRelationMapper relationMapper,
      KnowledgeBaseService knowledgeBaseService,
      DocumentPermissionService documentPermissionService) {
    try {
      for (Constructor<?> constructor : KnowledgeGraphService.class.getConstructors()) {
        if (constructor.getParameterCount() == 4) {
          return (KnowledgeGraphService)
              constructor.newInstance(
                  entityMapper, relationMapper, knowledgeBaseService, documentPermissionService);
        }
        if (constructor.getParameterCount() == 3) {
          return (KnowledgeGraphService)
              constructor.newInstance(entityMapper, relationMapper, knowledgeBaseService);
        }
        if (constructor.getParameterCount() == 2) {
          return (KnowledgeGraphService) constructor.newInstance(entityMapper, relationMapper);
        }
      }
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Unable to construct KnowledgeGraphService", e);
    }
    throw new AssertionError("No supported KnowledgeGraphService constructor found");
  }

  private KgEntity entity(Long id, Long knowledgeBaseId, Long documentId) {
    KgEntity entity = new KgEntity();
    entity.setId(id);
    entity.setKnowledgeBaseId(knowledgeBaseId);
    entity.setDocumentId(documentId);
    entity.setName("entity-" + id);
    return entity;
  }

  private KgRelation relation(
      Long id, Long knowledgeBaseId, Long documentId, Long sourceEntityId, Long targetEntityId) {
    KgRelation relation = new KgRelation();
    relation.setId(id);
    relation.setKnowledgeBaseId(knowledgeBaseId);
    relation.setDocumentId(documentId);
    relation.setSourceEntityId(sourceEntityId);
    relation.setTargetEntityId(targetEntityId);
    relation.setRelationType("related_to");
    return relation;
  }

  private Object findPath(
      KnowledgeGraphService service,
      Long knowledgeBaseId,
      Long sourceEntityId,
      Long targetEntityId,
      int maxHops) {
    try {
      for (java.lang.reflect.Method method : KnowledgeGraphService.class.getMethods()) {
        if (!method.getName().equals("findPath")) {
          continue;
        }
        if (method.getParameterCount() == 4) {
          return method.invoke(service, knowledgeBaseId, sourceEntityId, targetEntityId, maxHops);
        }
        if (method.getParameterCount() == 3) {
          return method.invoke(service, sourceEntityId, targetEntityId, maxHops);
        }
      }
    } catch (ReflectiveOperationException e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new AssertionError("Unable to invoke KnowledgeGraphService.findPath", e);
    }
    throw new AssertionError("No supported KnowledgeGraphService.findPath method found");
  }
}
