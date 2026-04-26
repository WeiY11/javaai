package com.example.javaai.assistant;

import com.example.javaai.retrieval.HybridSearchService;
import com.example.javaai.retrieval.SearchResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AgentTools {

    private final HybridSearchService hybridSearchService;

    public record KbSearchRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("用户的查询文本") String query,
            @JsonProperty(required = true) @JsonPropertyDescription("知识库ID") Long knowledgeBaseId,
            @JsonProperty(defaultValue = "5") @JsonPropertyDescription("返回结果数量") int topK
    ) {}

    public record KbSearchResponse(String results, String error) {}

    @Bean
    @Description("在指定知识库中检索相关文档内容。当用户提问涉及知识库中的信息时，调用此工具进行语义和关键词混合检索。")
    public Function<KbSearchRequest, KbSearchResponse> kbSearch() {
        return request -> {
            try {
                int topK = request.topK() > 0 ? request.topK() : 5;
                List<SearchResult> results = hybridSearchService.search(
                        request.query(), request.knowledgeBaseId(), topK);

                if (results.isEmpty()) {
                    return new KbSearchResponse("未找到相关内容", null);
                }

                String resultText = results.stream()
                        .map(r -> "[文档ID=" + r.getDocumentId() + " 切片#" + r.getChunkIndex()
                                + " 评分=" + String.format("%.3f", r.getScore()) + "]\n" + r.getContent())
                        .collect(Collectors.joining("\n\n"));

                return new KbSearchResponse(resultText, null);
            } catch (Exception e) {
                log.error("KB_SEARCH tool failed", e);
                return new KbSearchResponse(null, "检索失败: " + e.getMessage());
            }
        };
    }
}
