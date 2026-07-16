package com.example.evimind.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.evimind.extractor.ExtractionResult;
import com.example.evimind.service.AnalysisResultService;
import com.example.evimind.service.FileExtractorService;

class AiDataToolsTest {

  @TempDir Path dataDir;

  @Test
  void listDirectoryDoesNotExposePathTraversalDetails() {
    AiDataTools tools = createTools(mock(FileExtractorService.class), mock(AnalysisResultService.class));

    AiDataTools.ListDirectoryResponse response =
        tools.listDirectory().apply(new AiDataTools.ListDirectoryRequest("../outside"));

    assertNull(response.files());
    assertEquals("Unable to list directory.", response.error());
  }

  @Test
  void readFileDoesNotExposeExtractorFailureDetails() throws IOException {
    FileExtractorService fileExtractorService = mock(FileExtractorService.class);
    Path file = Files.writeString(dataDir.resolve("document.txt"), "content");
    when(fileExtractorService.extractFile(any(Path.class), any(Integer.class)))
        .thenReturn(ExtractionResult.failure("provider token is confidential"));
    AiDataTools tools = createTools(fileExtractorService, mock(AnalysisResultService.class));

    AiDataTools.ReadFileResponse response =
        tools.readFileContent().apply(new AiDataTools.ReadFileRequest(file.getFileName().toString()));

    assertNull(response.content());
    assertEquals("File extraction failed.", response.error());
  }

  @Test
  void queryHistoryDoesNotExposeRepositoryFailureDetails() {
    AnalysisResultService analysisResultService = mock(AnalysisResultService.class);
    when(analysisResultService.getResultsByFile("report.pdf"))
        .thenThrow(new RuntimeException("database password is confidential"));
    AiDataTools tools = createTools(mock(FileExtractorService.class), analysisResultService);

    AiDataTools.QueryAnalysisResponse response =
        tools.queryAnalysisHistory().apply(new AiDataTools.QueryAnalysisRequest("report.pdf"));

    assertNull(response.analysisResult());
    assertEquals("Unable to query analysis history.", response.error());
  }

  private AiDataTools createTools(
      FileExtractorService fileExtractorService, AnalysisResultService analysisResultService) {
    AiDataTools tools =
        new AiDataTools(fileExtractorService, analysisResultService, mock(AnalysisProperties.class));
    ReflectionTestUtils.setField(tools, "baseDir", dataDir.toString());
    return tools;
  }
}
