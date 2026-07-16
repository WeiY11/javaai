package com.example.evimind.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AnalysisErrorResponseContractTest {

  @Test
  void batchAnalysisResponsesDoNotIncludeExtractorOrExceptionMessages() throws IOException {
    String source =
        Files.readString(
            Path.of("src/main/java/com/example/evimind/controller/AnalysisController.java"),
            StandardCharsets.UTF_8);

    assertFalse(source.contains("extraction.getErrorMessage()"));
    assertFalse(source.contains("e.getMessage()"));
  }
}
