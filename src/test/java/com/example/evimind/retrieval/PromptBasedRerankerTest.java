package com.example.evimind.retrieval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

/** PromptBasedReranker 单元测试。 覆盖核心场景：正常重排序、单候选跳过、禁用回退、LLM 失败降级、分数解析。 */
@ExtendWith(MockitoExtension.class)
class PromptBasedRerankerTest {

  @Mock private Map<String, ChatClient> chatClients;

  @InjectMocks private PromptBasedReranker reranker;

  private List<SearchResult> sampleCandidates() {
    return List.of(
        new SearchResult("c1", 1L, 1L, "深度学习在图像识别中的应用非常广泛", 0, 0.90, "rrf_fused"),
        new SearchResult("c2", 2L, 1L, "自然语言处理技术的最新进展", 1, 0.80, "rrf_fused"),
        new SearchResult("c3", 3L, 1L, "卷积神经网络用于目标检测", 2, 0.70, "rrf_fused"));
  }

  @Test
  void shouldReturnOriginalOrderWhenDisabled() {
    ReflectionTestUtils.setField(reranker, "enabled", false);

    List<SearchResult> result = reranker.rerank("query", sampleCandidates(), 3);

    assertEquals(3, result.size());
    assertEquals("c1", result.get(0).getChunkId());
    verifyNoInteractions(chatClients);
  }

  @Test
  void shouldReturnSingleCandidateAsIs() {
    ReflectionTestUtils.setField(reranker, "enabled", true);
    List<SearchResult> single =
        List.of(new SearchResult("c1", 1L, 1L, "content", 0, 0.90, "rrf_fused"));

    List<SearchResult> result = reranker.rerank("query", single, 5);

    assertEquals(1, result.size());
    verifyNoInteractions(chatClients);
  }

  @Test
  void shouldRerankByLLMScores() {
    ReflectionTestUtils.setField(reranker, "enabled", true);
    ReflectionTestUtils.setField(reranker, "timeoutMs", 3000L);
    ReflectionTestUtils.setField(reranker, "maxCandidates", 10);

    ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(chatClients.isEmpty()).thenReturn(false);
    when(chatClients.containsKey("deepseek")).thenReturn(true);
    when(chatClients.get("deepseek")).thenReturn(chatClient);
    // LLM 认为第 3 个候选最相关，第 1 个次之，第 2 个最不相关
    when(chatClient.prompt().user(anyString()).call().content()).thenReturn("[6, 3, 9]");

    List<SearchResult> result = reranker.rerank("图像识别", sampleCandidates(), 3);

    assertEquals(3, result.size());
    // 第 3 个候选（分数 9/10 = 0.9）应该排在第一位
    assertEquals("c3", result.get(0).getChunkId());
    // 第 1 个候选（分数 6/10 = 0.6）应该排在第二位
    assertEquals("c1", result.get(1).getChunkId());
    // 第 2 个候选（分数 3/10 = 0.3）应该排在最后
    assertEquals("c2", result.get(2).getChunkId());
    // 验证 source 标记了 reranked
    assertTrue(result.get(0).getSource().contains("reranked"));
  }

  @Test
  void shouldFallbackToOriginalOrderWhenLLMFails() {
    ReflectionTestUtils.setField(reranker, "enabled", true);
    ReflectionTestUtils.setField(reranker, "timeoutMs", 3000L);
    ReflectionTestUtils.setField(reranker, "maxCandidates", 10);

    ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(chatClients.isEmpty()).thenReturn(false);
    when(chatClients.containsKey("deepseek")).thenReturn(true);
    when(chatClients.get("deepseek")).thenReturn(chatClient);
    when(chatClient.prompt().user(anyString()).call().content())
        .thenThrow(new RuntimeException("API error"));

    List<SearchResult> result = reranker.rerank("query", sampleCandidates(), 3);

    // 降级：保持原始 RRF 排序
    assertEquals("c1", result.get(0).getChunkId());
    assertEquals("c2", result.get(1).getChunkId());
    assertEquals("c3", result.get(2).getChunkId());
  }

  @Test
  void shouldFallbackWhenScoreCountMismatch() {
    ReflectionTestUtils.setField(reranker, "enabled", true);
    ReflectionTestUtils.setField(reranker, "timeoutMs", 3000L);
    ReflectionTestUtils.setField(reranker, "maxCandidates", 10);

    ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(chatClients.isEmpty()).thenReturn(false);
    when(chatClients.containsKey("deepseek")).thenReturn(true);
    when(chatClients.get("deepseek")).thenReturn(chatClient);
    // LLM 返回 2 个分数但期望 3 个
    when(chatClient.prompt().user(anyString()).call().content()).thenReturn("[8, 5]");

    List<SearchResult> result = reranker.rerank("query", sampleCandidates(), 3);

    // 降级：分数数量不匹配
    assertEquals("c1", result.get(0).getChunkId());
  }

  @Test
  void shouldLimitToTopN() {
    ReflectionTestUtils.setField(reranker, "enabled", true);
    ReflectionTestUtils.setField(reranker, "timeoutMs", 3000L);
    ReflectionTestUtils.setField(reranker, "maxCandidates", 10);

    ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(chatClients.isEmpty()).thenReturn(false);
    when(chatClients.containsKey("deepseek")).thenReturn(true);
    when(chatClients.get("deepseek")).thenReturn(chatClient);
    when(chatClient.prompt().user(anyString()).call().content()).thenReturn("[5, 9, 3]");

    List<SearchResult> result = reranker.rerank("query", sampleCandidates(), 2);

    assertEquals(2, result.size());
  }

  @Test
  void shouldFallbackWhenNoChatClientAvailable() {
    ReflectionTestUtils.setField(reranker, "enabled", true);
    when(chatClients.isEmpty()).thenReturn(true);

    List<SearchResult> result = reranker.rerank("query", sampleCandidates(), 3);

    assertEquals("c1", result.get(0).getChunkId());
  }

  @Test
  void shouldHandleScoresWithExplanations() {
    ReflectionTestUtils.setField(reranker, "enabled", true);
    ReflectionTestUtils.setField(reranker, "timeoutMs", 3000L);
    ReflectionTestUtils.setField(reranker, "maxCandidates", 10);

    ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(chatClients.isEmpty()).thenReturn(false);
    when(chatClients.containsKey("deepseek")).thenReturn(true);
    when(chatClients.get("deepseek")).thenReturn(chatClient);
    // LLM 返回带解释的分数
    when(chatClient.prompt().user(anyString()).call().content())
        .thenReturn("根据相关性评估，结果如下：[7, 4, 8]");

    List<SearchResult> result = reranker.rerank("query", sampleCandidates(), 3);

    assertEquals(3, result.size());
    // 第 3 个候选分数最高（8/10 = 0.8）
    assertEquals("c3", result.get(0).getChunkId());
  }
}
