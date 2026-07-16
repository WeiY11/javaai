package com.example.evimind.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LoggingConfigurationContractTest {

  @Test
  void defaultModelLibraryLoggingDoesNotEmitDebugPayloads() throws IOException {
    String applicationConfig = Files.readString(Path.of("src", "main", "resources", "application.yml"));

    assertThat(applicationConfig).containsPattern("(?m)^    org\\.springframework\\.ai: INFO$");
  }
}
