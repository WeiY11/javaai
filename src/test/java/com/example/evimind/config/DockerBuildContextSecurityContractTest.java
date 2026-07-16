package com.example.evimind.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DockerBuildContextSecurityContractTest {

  @Test
  void backendBuildContextExcludesSecretsLocalDataAndUnrelatedFrontendAssets() throws IOException {
    String dockerIgnore = Files.readString(Path.of(".dockerignore"));

    assertThat(dockerIgnore)
        .containsPattern("(?m)^\\.env$")
        .containsPattern("(?m)^\\.env\\.\\*$")
        .containsPattern("(?m)^\\.git$")
        .containsPattern("(?m)^data/$")
        .containsPattern("(?m)^target/$")
        .containsPattern("(?m)^frontend/$")
        .containsPattern("(?m)^\\*.log$");
  }

  @Test
  void frontendBuildContextExcludesSecretsDependenciesAndGeneratedOutput() throws IOException {
    String dockerIgnore = Files.readString(Path.of("frontend", ".dockerignore"));

    assertThat(dockerIgnore)
        .containsPattern("(?m)^\\.env$")
        .containsPattern("(?m)^\\.env\\.\\*$")
        .containsPattern("(?m)^node_modules/$")
        .containsPattern("(?m)^dist/$")
        .containsPattern("(?m)^\\*.log$");
  }
}
