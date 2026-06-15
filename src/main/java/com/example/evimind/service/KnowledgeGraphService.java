package com.example.evimind.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.evimind.mapper.KgEntityMapper;
import com.example.evimind.mapper.KgRelationMapper;
import com.example.evimind.model.entity.KgEntity;
import com.example.evimind.model.entity.KgRelation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 知识图谱查询服务。 支持邻居查询、多跳路径搜索、图谱统计等功能。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

  private final KgEntityMapper entityMapper;
  private final KgRelationMapper relationMapper;

  /** 获取知识库的完整图谱数据（实体 + 关系）。 */
  public Map<String, Object> getGraph(Long knowledgeBaseId) {
    List<KgEntity> entities = entityMapper.findByKnowledgeBaseId(knowledgeBaseId);
    List<KgRelation> relations = relationMapper.findByKnowledgeBaseId(knowledgeBaseId);

    List<Map<String, Object>> nodes =
        entities.stream()
            .map(
                e -> {
                  Map<String, Object> node = new LinkedHashMap<>();
                  node.put("id", e.getId());
                  node.put("name", e.getName());
                  node.put("type", e.getEntityType());
                  node.put("description", e.getDescription());
                  node.put("documentId", e.getDocumentId());
                  return node;
                })
            .collect(Collectors.toList());

    List<Map<String, Object>> edges =
        relations.stream()
            .map(
                r -> {
                  Map<String, Object> edge = new LinkedHashMap<>();
                  edge.put("id", r.getId());
                  edge.put("source", r.getSourceEntityId());
                  edge.put("target", r.getTargetEntityId());
                  edge.put("relation", r.getRelationType());
                  edge.put("documentId", r.getDocumentId());
                  return edge;
                })
            .collect(Collectors.toList());

    Map<String, Object> graph = new LinkedHashMap<>();
    graph.put("nodes", nodes);
    graph.put("edges", edges);
    graph.put("stats", buildStats(entities, relations));
    return graph;
  }

  /** 获取指定实体的邻居节点和关系。 */
  public Map<String, Object> getNeighbors(Long entityId) {
    KgEntity entity = entityMapper.selectById(entityId);
    if (entity == null) {
      throw new IllegalArgumentException("Entity not found: " + entityId);
    }

    List<KgEntity> neighbors = entityMapper.findNeighbors(entityId);
    List<KgRelation> relations = relationMapper.findByEntityId(entityId);

    // 构建关系方向信息
    List<Map<String, Object>> connections = new ArrayList<>();
    for (KgRelation rel : relations) {
      Map<String, Object> conn = new LinkedHashMap<>();
      conn.put("relationId", rel.getId());
      conn.put("relationType", rel.getRelationType());
      if (rel.getSourceEntityId().equals(entityId)) {
        conn.put("direction", "outgoing");
        KgEntity target = entityMapper.selectById(rel.getTargetEntityId());
        if (target != null) {
          conn.put("neighborId", target.getId());
          conn.put("neighborName", target.getName());
          conn.put("neighborType", target.getEntityType());
        }
      } else {
        conn.put("direction", "incoming");
        KgEntity source = entityMapper.selectById(rel.getSourceEntityId());
        if (source != null) {
          conn.put("neighborId", source.getId());
          conn.put("neighborName", source.getName());
          conn.put("neighborType", source.getEntityType());
        }
      }
      connections.add(conn);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "entity",
        Map.of(
            "id",
            entity.getId(),
            "name",
            entity.getName(),
            "type",
            entity.getEntityType() != null ? entity.getEntityType() : "",
            "description",
            entity.getDescription() != null ? entity.getDescription() : ""));
    result.put(
        "neighbors",
        neighbors.stream()
            .map(
                n -> {
                  Map<String, Object> nMap = new LinkedHashMap<>();
                  nMap.put("id", n.getId());
                  nMap.put("name", n.getName());
                  nMap.put("type", n.getEntityType());
                  return nMap;
                })
            .collect(Collectors.toList()));
    result.put("connections", connections);
    return result;
  }

  /** BFS 多跳路径搜索：从 sourceEntity 到 targetEntity 的最短路径。 最大搜索深度由 maxHops 控制。 */
  public List<Map<String, Object>> findPath(Long sourceEntityId, Long targetEntityId, int maxHops) {
    if (maxHops <= 0) maxHops = 3;
    if (maxHops > 6) maxHops = 6;

    KgEntity source = entityMapper.selectById(sourceEntityId);
    KgEntity target = entityMapper.selectById(targetEntityId);
    if (source == null || target == null) {
      throw new IllegalArgumentException("Source or target entity not found");
    }

    // BFS
    Queue<List<Long>> queue = new LinkedList<>();
    Set<Long> visited = new HashSet<>();
    Map<Long, Long> parent = new HashMap<>();
    Map<Long, KgRelation> parentRelation = new HashMap<>();

    queue.add(List.of(sourceEntityId));
    visited.add(sourceEntityId);

    while (!queue.isEmpty()) {
      List<Long> path = queue.poll();
      Long current = path.get(path.size() - 1);

      if (path.size() > maxHops + 1) break;

      if (current.equals(targetEntityId)) {
        return buildPathResult(path, parentRelation);
      }

      List<KgRelation> relations = relationMapper.findByEntityId(current);
      for (KgRelation rel : relations) {
        Long next =
            rel.getSourceEntityId().equals(current)
                ? rel.getTargetEntityId()
                : rel.getSourceEntityId();

        if (!visited.contains(next)) {
          visited.add(next);
          parent.put(next, current);
          parentRelation.put(next, rel);

          List<Long> newPath = new ArrayList<>(path);
          newPath.add(next);
          queue.add(newPath);
        }
      }
    }

    return Collections.emptyList(); // No path found
  }

  /** 获取知识库图谱的统计信息。 */
  public Map<String, Object> getStats(Long knowledgeBaseId) {
    List<KgEntity> entities = entityMapper.findByKnowledgeBaseId(knowledgeBaseId);
    List<KgRelation> relations = relationMapper.findByKnowledgeBaseId(knowledgeBaseId);
    return buildStats(entities, relations);
  }

  // ==================== 内部方法 ====================

  private Map<String, Object> buildStats(List<KgEntity> entities, List<KgRelation> relations) {
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalEntities", entities.size());
    stats.put("totalRelations", relations.size());

    // 实体类型分布
    Map<String, Long> typeDist =
        entities.stream()
            .filter(e -> e.getEntityType() != null)
            .collect(Collectors.groupingBy(KgEntity::getEntityType, Collectors.counting()));
    stats.put("entityTypeDistribution", typeDist);

    // 关系类型分布
    Map<String, Long> relDist =
        relations.stream()
            .collect(Collectors.groupingBy(KgRelation::getRelationType, Collectors.counting()));
    stats.put("relationTypeDistribution", relDist);

    // 度最高的实体 (hub nodes)
    Map<Long, Long> degree = new HashMap<>();
    for (KgRelation r : relations) {
      degree.merge(r.getSourceEntityId(), 1L, Long::sum);
      degree.merge(r.getTargetEntityId(), 1L, Long::sum);
    }
    List<Map<String, Object>> topEntities =
        degree.entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
            .limit(10)
            .map(
                entry -> {
                  KgEntity entity = entityMapper.selectById(entry.getKey());
                  Map<String, Object> item = new LinkedHashMap<>();
                  if (entity != null) {
                    item.put("id", entity.getId());
                    item.put("name", entity.getName());
                    item.put("type", entity.getEntityType());
                  }
                  item.put("degree", entry.getValue());
                  return item;
                })
            .collect(Collectors.toList());
    stats.put("hubEntities", topEntities);

    return stats;
  }

  private List<Map<String, Object>> buildPathResult(
      List<Long> path, Map<Long, KgRelation> parentRelation) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < path.size(); i++) {
      Long entityId = path.get(i);
      KgEntity entity = entityMapper.selectById(entityId);
      Map<String, Object> step = new LinkedHashMap<>();
      step.put("position", i);
      if (entity != null) {
        step.put("entityId", entity.getId());
        step.put("entityName", entity.getName());
        step.put("entityType", entity.getEntityType());
      }
      if (i > 0) {
        KgRelation rel = parentRelation.get(entityId);
        if (rel != null) {
          step.put("viaRelation", rel.getRelationType());
          step.put("relationId", rel.getId());
        }
      }
      result.add(step);
    }
    return result;
  }
}
