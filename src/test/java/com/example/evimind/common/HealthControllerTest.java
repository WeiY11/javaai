package com.example.evimind.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.evimind.storage.MinioStorageService;

class HealthControllerTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(DataSource.class, HealthControllerTest::healthyDataSourceBean);

  @Test
  void unconfiguredOptionalComponentsIncludeActionableDiagnostics() throws Exception {
    HealthController controller = new HealthController();
    ReflectionTestUtils.setField(controller, "dataSource", healthyDataSource());

    Map<String, Object> health = controller.health().getData();
    Map<String, Object> components = componentMap(health);
    Map<String, Object> elasticsearch = componentStatus(components, "elasticsearch");
    Map<String, Object> minio = componentStatus(components, "minio");

    assertThat(health.get("status")).isEqualTo("DEGRADED");
    assertThat(elasticsearch)
        .containsEntry("status", "NOT_CONFIGURED")
        .containsEntry("message", "Elasticsearch client is not configured")
        .containsEntry("action", "Set ES_URIS and start Elasticsearch to enable indexing and full-text retrieval");
    assertThat(minio)
        .containsEntry("status", "NOT_CONFIGURED")
        .containsEntry("message", "MinIO storage service is not configured")
        .containsEntry("action", "Set MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, and MINIO_BUCKET");
  }

  @Test
  void configuredMinioServiceIsCheckedBeforeReportingUp() throws Exception {
    HealthController controller = new HealthController();
    ReflectionTestUtils.setField(controller, "dataSource", healthyDataSource());
    MinioStorageService minioStorageService = mock(MinioStorageService.class);
    ReflectionTestUtils.setField(controller, "minioStorageService", minioStorageService);

    Map<String, Object> health = controller.health().getData();
    Map<String, Object> minio = componentStatus(componentMap(health), "minio");

    assertThat(minio)
        .containsEntry("status", "DOWN")
        .containsEntry("message", "MinIO health check did not run");
  }

  @Test
  void disabledElasticsearchIsNotReportedUpEvenWhenTemplateBeanExists() {
    ElasticsearchTemplate elasticsearchTemplate = mock(ElasticsearchTemplate.class);
    when(elasticsearchTemplate.execute(any())).thenReturn(true);

    contextRunner
        .withPropertyValues("spring.elasticsearch.enabled=false")
        .withBean(ElasticsearchTemplate.class, () -> elasticsearchTemplate)
        .withBean(HealthController.class)
        .run(
            context -> {
              Map<String, Object> health = context.getBean(HealthController.class).health().getData();
              Map<String, Object> elasticsearch =
                  componentStatus(componentMap(health), "elasticsearch");

              assertThat(health.get("status")).isEqualTo("DEGRADED");
              assertThat(elasticsearch)
                  .containsEntry("status", "NOT_CONFIGURED")
                  .containsEntry("message", "Elasticsearch client is disabled by configuration")
                  .containsEntry(
                      "action",
                      "Set spring.elasticsearch.enabled=true and ES_URIS to enable full-text retrieval");
            });
  }

  @Test
  void disabledOptionalComponentsDoNotDegradeStandaloneHealth() {
    contextRunner
        .withPropertyValues("spring.elasticsearch.enabled=false", "minio.enabled=false")
        .withBean(HealthController.class)
        .run(
            context -> {
              Map<String, Object> health = context.getBean(HealthController.class).health().getData();
              Map<String, Object> components = componentMap(health);

              assertThat(health.get("status")).isEqualTo("UP");
              assertThat(componentStatus(components, "postgresql"))
                  .containsEntry("status", "UP")
                  .containsEntry("required", true);
              assertThat(componentStatus(components, "elasticsearch"))
                  .containsEntry("status", "NOT_CONFIGURED")
                  .containsEntry("required", false);
              assertThat(componentStatus(components, "minio"))
                  .containsEntry("status", "NOT_CONFIGURED")
                  .containsEntry("required", false);
            });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> componentMap(Map<String, Object> health) {
    return (Map<String, Object>) health.get("components");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> componentStatus(
      Map<String, Object> components, String componentName) {
    return (Map<String, Object>) components.get(componentName);
  }

  private static DataSource healthyDataSource() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(3)).thenReturn(true);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
    when(metaData.getDatabaseProductVersion()).thenReturn("17");

    return dataSource;
  }

  private static DataSource healthyDataSourceBean() {
    try {
      return healthyDataSource();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create healthy datasource test double", e);
    }
  }
}
