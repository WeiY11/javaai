package com.example.evimind.assistant;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import com.example.evimind.retrieval.HybridSearchService;
import com.example.evimind.retrieval.SearchResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AgentTools {

  private static final int DEFAULT_TOP_K = 5;
  private static final int MAX_TOP_K = 10;
  private static final int MAX_TOOL_OUTPUT_CHARS = 4000;

  private final HybridSearchService hybridSearchService;

  public record KbSearchRequest(
      @JsonProperty(required = true) @JsonPropertyDescription("用户查询文本") String query,
      @JsonProperty(required = true) @JsonPropertyDescription("知识库ID") Long knowledgeBaseId,
      @JsonProperty(defaultValue = "5") @JsonPropertyDescription("返回结果数量") int topK) {}

  public record KbSearchResponse(String results, String error) {}

  @Bean
  @Description("在指定知识库中检索相关文档内容。当用户提问涉及知识库信息时，调用此工具进行语义和关键词混合检索。")
  public Function<KbSearchRequest, KbSearchResponse> kbSearch() {
    return request -> {
      try {
        int topK = Math.min(MAX_TOP_K, request.topK() > 0 ? request.topK() : DEFAULT_TOP_K);
        List<SearchResult> results =
            hybridSearchService.search(request.query(), request.knowledgeBaseId(), topK);

        if (results.isEmpty()) {
          return new KbSearchResponse("未找到相关内容", null);
        }

        String resultText = formatResults(results);
        return new KbSearchResponse(limitOutput(resultText), null);
      } catch (Exception e) {
        log.error("KB_SEARCH tool failed", e);
        return new KbSearchResponse(null, "检索失败: " + e.getMessage());
      }
    };
  }

  private String limitOutput(String resultText) {
    if (resultText.length() <= MAX_TOOL_OUTPUT_CHARS) {
      return resultText;
    }
    return resultText.substring(0, MAX_TOOL_OUTPUT_CHARS);
  }

  private String formatResults(List<SearchResult> results) {
    return results.stream()
        .map(
            result ->
                "[文档ID="
                    + result.getDocumentId()
                    + " 切片#"
                    + result.getChunkIndex()
                    + " 置信度="
                    + String.format("%.3f", result.getScore())
                    + "]\n"
                    + result.getContent())
        .collect(Collectors.joining("\n\n"));
  }
}
