package com.example.evimind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

  private String endpoint;
  private String accessKey;
  private String secretKey;
  private String bucket;

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = "minio",
      name = "enabled",
      havingValue = "true")
  public MinioClient minioClient() {
    return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
  }
}
