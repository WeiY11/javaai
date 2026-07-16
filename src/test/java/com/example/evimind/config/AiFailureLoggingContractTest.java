package com.example.evimind.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class AiFailureLoggingContractTest {

  private static final Pattern RAW_THROWABLE_LOG =
      Pattern.compile("log\\.(?:trace|debug|info|warn|error)\\([^;]*?,\\s*e\\)\\s*;", Pattern.DOTALL);

  private static final List<Path> AI_FAILURE_PATHS =
      List.of(
          Path.of("src/main/java/com/example/evimind/assistant/ConversationService.java"),
          Path.of("src/main/java/com/example/evimind/assistant/AgentTools.java"),
          Path.of("src/main/java/com/example/evimind/ingestion/EmbeddingService.java"),
          Path.of("src/main/java/com/example/evimind/ingestion/EntityRelationExtractor.java"),
          Path.of("src/main/java/com/example/evimind/knowledgebase/KnowledgeBaseService.java"),
          Path.of("src/main/java/com/example/evimind/qa/RagPipeline.java"),
          Path.of("src/main/java/com/example/evimind/retrieval/PromptBasedReranker.java"),
          Path.of("src/main/java/com/example/evimind/retrieval/QueryRewriteService.java"),
          Path.of("src/main/java/com/example/evimind/service/AutoReportService.java"),
          Path.of("src/main/java/com/example/evimind/service/LiteratureReviewService.java"));

  @Test
  void aiFailureLogsDoNotAttachRawThrowablesOrExceptionMessages() throws IOException {
    List<String> violations = new ArrayList<>();
    for (Path sourcePath : AI_FAILURE_PATHS) {
      String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
      if (RAW_THROWABLE_LOG.matcher(source).find() || source.contains("e.getMessage()")) {
        violations.add(sourcePath.toString());
      }
    }

    assertTrue(
        violations.isEmpty(),
        () -> "AI failure logs must not attach raw throwables or exception messages: " + violations);
  }
}
