package com.example.evimind.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import com.example.evimind.extractor.ExtractionResult;
import com.example.evimind.model.AnalysisResult;
import com.example.evimind.service.AnalysisResultService;
import com.example.evimind.service.FileExtractorService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@Configuration
public class AiDataTools {

  @Value("${custom.data.base-dir}")
  private String baseDir;

  private final FileExtractorService fileExtractorService;
  private final AnalysisResultService analysisResultService;
  private final AnalysisProperties analysisProperties;

  public AiDataTools(
      FileExtractorService fileExtractorService,
      AnalysisResultService analysisResultService,
      AnalysisProperties analysisProperties) {
    this.fileExtractorService = fileExtractorService;
    this.analysisResultService = analysisResultService;
    this.analysisProperties = analysisProperties;
  }

  private Path safePath(String relativePath) {
    Path base = Paths.get(baseDir).toAbsolutePath().normalize();
    Path target =
        base.resolve(relativePath == null ? "" : relativePath).toAbsolutePath().normalize();
    if (!target.startsWith(base)) {
      throw new SecurityException("Path traversal detected: " + relativePath);
    }
    return target;
  }

  public record ListDirectoryRequest(
      @JsonProperty(required = true) @JsonPropertyDescription("相对于数据根目录的相对路径。根目录留空字符串即可。")
          String dirPath) {}

  public record ListDirectoryResponse(List<String> files, String error) {}

  @Bean
  @Description("获取指定目录下的所有文件和文件夹列表。如果不确定当前有哪些文件，可调用此工具。")
  public Function<ListDirectoryRequest, ListDirectoryResponse> listDirectory() {
    return request -> {
      try {
        Path dirPath = safePath(request.dirPath());
        File folder = dirPath.toFile();
        if (!folder.exists() || !folder.isDirectory()) {
          return new ListDirectoryResponse(null, "目录不存在: " + request.dirPath());
        }
        File[] children = folder.listFiles();
        List<String> items = new ArrayList<>();
        if (children != null) {
          for (File f : children) {
            if (f.getName().startsWith(".") || f.getName().equals("__pycache__")) continue;
            items.add((f.isDirectory() ? "[目录] " : "[文件] ") + f.getName());
          }
        }
        return new ListDirectoryResponse(items, null);
      } catch (Exception e) {
        return new ListDirectoryResponse(null, "获取目录失败: " + e.getMessage());
      }
    };
  }

  public record ReadFileRequest(
      @JsonProperty(required = true) @JsonPropertyDescription("要读取的文件的相对路径") String filePath) {}

  public record ReadFileResponse(String content, String error) {}

  @Bean
  @Description("读取并提取指定文件的文本内容，支持 PDF, Word, Excel, CSV, JSON, Markdown 等多种格式。")
  public Function<ReadFileRequest, ReadFileResponse> readFileContent() {
    return request -> {
      try {
        Path target = safePath(request.filePath());
        File file = target.toFile();
        if (!file.exists() || !file.isFile()) {
          return new ReadFileResponse(null, "文件不存在: " + request.filePath());
        }
        ExtractionResult extraction =
            fileExtractorService.extractFile(target, analysisProperties.getMaxPromptSize());
        if (!extraction.isSuccess()) {
          return new ReadFileResponse(null, "提取失败: " + extraction.getErrorMessage());
        }
        String content = extraction.getContent();
        if (content.length() > 50000) {
          content = content.substring(0, 50000) + "\n...[内容过长已截断]";
        }
        return new ReadFileResponse(content, null);
      } catch (Exception e) {
        return new ReadFileResponse(null, "读取文件失败: " + e.getMessage());
      }
    };
  }

  public record QueryAnalysisRequest(
      @JsonProperty(required = true) @JsonPropertyDescription("要查询历史分析结果的文件相对路径。")
          String filePath) {}

  public record QueryAnalysisResponse(String analysisResult, String error) {}

  @Bean
  @Description("查询某文件以前是否被系统分析过，返回系统自动生成的分析报告。")
  public Function<QueryAnalysisRequest, QueryAnalysisResponse> queryAnalysisHistory() {
    return request -> {
      try {
        List<AnalysisResult> results = analysisResultService.getResultsByFile(request.filePath());
        if (results == null || results.isEmpty()) {
          return new QueryAnalysisResponse("暂无该文件的分析记录", null);
        }
        return new QueryAnalysisResponse(results.get(0).getContent(), null);
      } catch (Exception e) {
        return new QueryAnalysisResponse(null, "查询历史记录失败: " + e.getMessage());
      }
    };
  }
}
