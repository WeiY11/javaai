package com.example.evimind.common;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.evimind.model.dto.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/** 增强型健康检查端点。 检测各组件（PostgreSQL、Elasticsearch、MinIO）的连通性状态。 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  @Autowired private DataSource dataSource;

  @Autowired(required = false)
  private ElasticsearchTemplate elasticsearchTemplate;

  @Value("${spring.elasticsearch.enabled:true}")
  private boolean elasticsearchEnabled = true;

  @Value("${minio.enabled:true}")
  private boolean minioEnabled = true;

  @Autowired(required = false)
  private com.example.evimind.storage.MinioStorageService minioStorageService;

  @GetMapping
  public ApiResponse<Map<String, Object>> health() {
    Map<String, Object> components = new LinkedHashMap<>();
    boolean allUp = true;

    // PostgreSQL
    components.put("postgresql", checkPostgres());
    if (isRequiredComponentUnhealthy((Map<?, ?>) components.get("postgresql"))) allUp = false;

    // Elasticsearch
    components.put("elasticsearch", checkElasticsearch());
    if (isRequiredComponentUnhealthy((Map<?, ?>) components.get("elasticsearch"))) allUp = false;

    // MinIO
    components.put("minio", checkMinio());
    if (isRequiredComponentUnhealthy((Map<?, ?>) components.get("minio"))) allUp = false;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", allUp ? "UP" : "DEGRADED");
    result.put("service", "evimind");
    result.put("components", components);

    return ApiResponse.success(result);
  }

  private Map<String, Object> checkPostgres() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("required", true);
    try (Connection conn = dataSource.getConnection()) {
      boolean valid = conn.isValid(3);
      status.put("status", valid ? "UP" : "DOWN");
      status.put("database", conn.getMetaData().getDatabaseProductName());
      status.put("version", conn.getMetaData().getDatabaseProductVersion());
      if (!valid) {
        status.put("message", "PostgreSQL connection is not valid");
        status.put("action", "Check POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, and POSTGRES_PASSWORD");
      }
    } catch (Exception e) {
      status.put("status", "DOWN");
      status.put("error", e.getMessage());
      status.put("message", "PostgreSQL health check failed");
      status.put("action", "Check POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, and POSTGRES_PASSWORD");
      log.warn("PostgreSQL health check failed: {}", e.getMessage());
    }
    return status;
  }

  private Map<String, Object> checkElasticsearch() {
    Map<String, Object> status = new LinkedHashMap<>();
    if (!elasticsearchEnabled) {
      status.put("status", "NOT_CONFIGURED");
      status.put("required", false);
      status.put("message", "Elasticsearch client is disabled by configuration");
      status.put(
          "action",
          "Set spring.elasticsearch.enabled=true and ES_URIS to enable full-text retrieval");
      return status;
    }
    status.put("required", true);
    if (elasticsearchTemplate == null) {
      status.put("status", "NOT_CONFIGURED");
      status.put("message", "Elasticsearch client is not configured");
      status.put("action", "Set ES_URIS and start Elasticsearch to enable indexing and full-text retrieval");
      return status;
    }
    try {
      boolean available =
          elasticsearchTemplate.execute(
              client -> {
                client.info();
                return true;
              });
      status.put("status", available ? "UP" : "DOWN");
      if (!available) {
        status.put("message", "Elasticsearch health check failed");
        status.put("action", "Check ES_URIS and Elasticsearch availability");
      }
    } catch (Exception e) {
      status.put("status", "DOWN");
      status.put("error", e.getMessage());
      status.put("message", "Elasticsearch health check failed");
      status.put("action", "Check ES_URIS and Elasticsearch availability");
      log.warn("Elasticsearch health check failed: {}", e.getMessage());
    }
    return status;
  }

  private Map<String, Object> checkMinio() {
    Map<String, Object> status = new LinkedHashMap<>();
    if (!minioEnabled) {
      status.put("status", "NOT_CONFIGURED");
      status.put("required", false);
      status.put("message", "MinIO storage service is disabled by configuration");
      status.put(
          "action",
          "Set minio.enabled=true, MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, and MINIO_BUCKET");
      return status;
    }
    status.put("required", true);
    if (minioStorageService == null) {
      status.put("status", "NOT_CONFIGURED");
      status.put("message", "MinIO storage service is not configured");
      status.put("action", "Set MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, and MINIO_BUCKET");
      return status;
    }
    Map<String, Object> storageStatus = minioStorageService.checkHealth();
    if (storageStatus == null || storageStatus.isEmpty()) {
      status.put("status", "DOWN");
      status.put("message", "MinIO health check did not run");
      status.put("action", "Check MinIO service wiring and health-check logs");
      return status;
    }
    storageStatus.putIfAbsent("required", true);
    return storageStatus;
  }

  private boolean isRequiredComponentUnhealthy(Map<?, ?> status) {
    boolean required = !Boolean.FALSE.equals(status.get("required"));
    return required && !"UP".equals(status.get("status"));
  }
}
