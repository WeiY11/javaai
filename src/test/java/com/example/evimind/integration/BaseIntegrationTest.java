package com.example.evimind.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类。
 * 使用 Testcontainers 启动 PostgreSQL 容器，提供真实的数据库环境。
 *
 * 子类使用 @SpringBootTest 注解即可启动完整的集成测试环境。
 * 注意：Elasticsearch 和 MinIO 使用 Mock 或跳过（可选扩展）。
 */
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("evimind_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Flyway for tests or use test-specific migrations
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        // Disable Elasticsearch for integration tests
        registry.add("spring.data.elasticsearch.repositories.enabled", () -> false);
        registry.add("spring.elasticsearch.uris", () -> "");
        // Use a dummy JWT secret for tests
        registry.add("jwt.secret", () -> "test-secret-key-for-integration-testing-must-be-long-enough");
    }
}
