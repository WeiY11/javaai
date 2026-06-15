package com.example.evimind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** 自定义 Micrometer 指标配置。 为 RAG 管道和 ETL 管道注册专用指标。 */
@Configuration
public class MetricsConfig {

  @Bean
  public Timer ragQueryTimer(MeterRegistry registry) {
    return Timer.builder("rag.query.duration")
        .description("RAG full pipeline duration (retrieve + generate)")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry);
  }

  @Bean
  public Timer ragSearchBackendTimer(MeterRegistry registry) {
    return Timer.builder("rag.search.backend.duration")
        .description("Per-backend search duration")
        .tag("backend", "unknown")
        .publishPercentiles(0.5, 0.95)
        .register(registry);
  }

  @Bean
  public DistributionSummary ragEvidenceScoreSummary(MeterRegistry registry) {
    return DistributionSummary.builder("rag.evidence.score")
        .description("Evidence confidence score distribution")
        .minimumExpectedValue(0.0)
        .maximumExpectedValue(1.0)
        .register(registry);
  }

  @Bean
  public Counter ragEvidenceSufficientCounter(MeterRegistry registry) {
    return Counter.builder("rag.evidence.status")
        .description("Evidence sufficiency count")
        .tag("status", "SUFFICIENT")
        .register(registry);
  }

  @Bean
  public Counter ragEvidenceInsufficientCounter(MeterRegistry registry) {
    return Counter.builder("rag.evidence.status")
        .description("Evidence sufficiency count")
        .tag("status", "INSUFFICIENT")
        .register(registry);
  }

  @Bean
  public Counter ragSearchDegradedCounter(MeterRegistry registry) {
    return Counter.builder("rag.search.degraded")
        .description("Degraded search count (one backend failed)")
        .register(registry);
  }

  @Bean
  public Timer etlDocumentTimer(MeterRegistry registry) {
    return Timer.builder("etl.document.duration")
        .description("Document ETL pipeline duration")
        .publishPercentiles(0.5, 0.95)
        .register(registry);
  }

  @Bean
  public Counter etlDocumentSuccessCounter(MeterRegistry registry) {
    return Counter.builder("etl.document.status")
        .description("Document ETL result count")
        .tag("status", "SUCCESS")
        .register(registry);
  }

  @Bean
  public Counter etlDocumentFailCounter(MeterRegistry registry) {
    return Counter.builder("etl.document.status")
        .description("Document ETL result count")
        .tag("status", "FAILED")
        .register(registry);
  }

  @Bean
  public Counter kgExtractionCounter(MeterRegistry registry) {
    return Counter.builder("kg.extraction.total")
        .description("Knowledge graph triple extraction count")
        .register(registry);
  }

  @Bean
  public Counter citationExtractionCounter(MeterRegistry registry) {
    return Counter.builder("citation.extraction.total")
        .description("Citation link extraction count")
        .register(registry);
  }
}
