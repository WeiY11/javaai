package com.example.evimind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

@Configuration
@ConditionalOnProperty(
    name = "spring.elasticsearch.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ElasticsearchConfig extends ElasticsearchConfiguration {

  @Value("${spring.elasticsearch.uris:http://localhost:9200}")
  private String esUris;

  @Override
  public ClientConfiguration clientConfiguration() {
    return ClientConfiguration.builder()
        .connectedTo(esUris.replace("http://", "").split(":")[0])
        .build();
  }
}
