package com.example.evimind.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DockerComposeSecurityContractTest {

  @Test
  void primaryComposeRequiresSecretsInsteadOfFallingBackToKnownCredentials() throws IOException {
    String compose = Files.readString(Path.of("docker-compose.yml"));

    assertThat(compose)
        .contains("${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}")
        .contains("${MINIO_ACCESS_KEY:?MINIO_ACCESS_KEY must be set}")
        .contains("${MINIO_SECRET_KEY:?MINIO_SECRET_KEY must be set}")
        .contains("${JWT_SECRET:?JWT_SECRET must be set}")
        .doesNotContain("POSTGRES_PASSWORD:-evimind123")
        .doesNotContain("MINIO_ACCESS_KEY:-minioadmin")
        .doesNotContain("MINIO_SECRET_KEY:-minioadmin")
        .doesNotContain("JWT_SECRET:-myDefaultJwtSecretKeyForDevOnlyPleaseReplaceInProduction2026");
  }

  @Test
  void exampleEnvironmentLeavesRequiredSecretsUnset() throws IOException {
    String example = Files.readString(Path.of(".env.example"));

    assertThat(example)
        .containsPattern("(?m)^POSTGRES_PASSWORD=$")
        .containsPattern("(?m)^MINIO_ACCESS_KEY=$")
        .containsPattern("(?m)^MINIO_SECRET_KEY=$")
        .containsPattern("(?m)^JWT_SECRET=$")
        .containsPattern("(?m)^GRAFANA_ADMIN_USER=$")
        .containsPattern("(?m)^GRAFANA_ADMIN_PASSWORD=$");
  }

  @Test
  void composeKeepsInfrastructureOnLoopbackAndMakesLocalPrometheusAccessExplicit()
      throws IOException {
    String compose = Files.readString(Path.of("docker-compose.yml"));

    assertThat(compose)
        .contains("\"127.0.0.1:8080:8080\"")
        .contains("\"127.0.0.1:9200:9200\"")
        .contains("CUSTOM_MANAGEMENT_PROMETHEUS_REQUIRE_ADMIN: false");
  }

  @Test
  void monitoringComposeRequiresGrafanaAdministratorCredentials() throws IOException {
    String compose = Files.readString(Path.of("docker-compose.grafana.yml"));

    assertThat(compose)
        .contains("${GRAFANA_ADMIN_USER:?GRAFANA_ADMIN_USER must be set}")
        .contains("${GRAFANA_ADMIN_PASSWORD:?GRAFANA_ADMIN_PASSWORD must be set}")
        .doesNotContain("GF_SECURITY_ADMIN_USER=admin")
        .doesNotContain("GF_SECURITY_ADMIN_PASSWORD=evimind");
  }
}
