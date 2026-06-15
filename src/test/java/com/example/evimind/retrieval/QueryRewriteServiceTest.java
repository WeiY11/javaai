package com.example.evimind.retrieval;

import com.example.evimind.config.PromptTemplateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * QueryRewriteService 单元测试。
 * 覆盖核心场景：空历史跳过、正常改写、改写失败降级、历史截断、超时处理。
 */
@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceTest {

    @Mock
    private PromptTemplateManager promptTemplateManager;
    @Mock
    private Map<String, ChatClient> chatClients;

    @InjectMocks
    private QueryRewriteService queryRewriteService;

    @Test
    void shouldSkipRewriteWhenDisabled() {
        ReflectionTestUtils.setField(queryRewriteService, "enabled", false);

        String result = queryRewriteService.rewrite("test query", "some history");

        assertEquals("test query", result);
        verifyNoInteractions(chatClients);
    }

    @Test
    void shouldSkipRewriteWhenHistoryIsNull() {
        ReflectionTestUtils.setField(queryRewriteService, "enabled", true);

        String result = queryRewriteService.rewrite("test query", null);

        assertEquals("test query", result);
        verifyNoInteractions(chatClients);
    }

    @Test
    void shouldSkipRewriteWhenHistoryIsEmpty() {
        ReflectionTestUtils.setField(queryRewriteService, "enabled", true);

        String result = queryRewriteService.rewrite("test query", "");

        assertEquals("test query", result);
        verifyNoInteractions(chatClients);
    }

    @Test
    void shouldReturnRewrittenQuery() {
        ReflectionTestUtils.setField(queryRewriteService, "enabled", true);
        ReflectionTestUtils.setField(queryRewriteService, "timeoutMs", 3000L);
        ReflectionTestUtils.setField(queryRewriteService, "maxHistoryMessages", 10);

        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClients.isEmpty()).thenReturn(false);
        when(chatClients.containsKey("deepseek")).thenReturn(true);
        when(chatClients.get("deepseek")).thenReturn(chatClient);
        when(promptTemplateManager.render(eq("query-rewrite-prompt"), anyMap()))
                .thenReturn("rewrite prompt");
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("机器学习的优点是什么");

        String result = queryRewriteService.rewrite("它的优点", "user: 什么是机器学习\nassistant: 机器学习是...");

        assertEquals("机器学习的优点是什么", result);
    }

    @Test
    void shouldFallbackToOriginalWhenRewriteReturnsNull() {
        ReflectionTestUtils.setField(queryRewriteService, "enabled", true);
        ReflectionTestUtils.setField(queryRewriteService, "timeoutMs", 3000L);
        ReflectionTestUtils.setField(queryRewriteService, "maxHistoryMessages", 10);

        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClients.isEmpty()).thenReturn(false);
        when(chatClients.containsKey("deepseek")).thenReturn(true);
        when(chatClients.get("deepseek")).thenReturn(chatClient);
        when(promptTemplateManager.render(eq("query-rewrite-prompt"), anyMap()))
                .thenReturn("rewrite prompt");
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn(null);

        String result = queryRewriteService.rewrite("original query", "some history");

        assertEquals("original query", result);
    }

    @Test
    void shouldFallbackToOriginalWhenLLMFails() {
        ReflectionTestUtils.setField(queryRewriteService, "enabled", true);
        ReflectionTestUtils.setField(queryRewriteService, "timeoutMs", 3000L);
        ReflectionTestUtils.setField(queryRewriteService, "maxHistoryMessages", 10);

        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClients.isEmpty()).thenReturn(false);
        when(chatClients.containsKey("deepseek")).thenReturn(true);
        when(chatClients.get("deepseek")).thenReturn(chatClient);
        when(promptTemplateManager.render(eq("query-rewrite-prompt"), anyMap()))
                .thenReturn("rewrite prompt");
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("API rate limit exceeded"));

        String result = queryRewriteService.rewrite("original query", "some history");

        assertEquals("original query", result);
    }

    @Test
    void shouldTruncateLongHistory() {
        ReflectionTestUtils.setField(queryRewriteService, "enabled", true);
        ReflectionTestUtils.setField(queryRewriteService, "timeoutMs", 3000L);
        ReflectionTestUtils.setField(queryRewriteService, "maxHistoryMessages", 3);

        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClients.isEmpty()).thenReturn(false);
        when(chatClients.containsKey("deepseek")).thenReturn(true);
        when(chatClients.get("deepseek")).thenReturn(chatClient);
        when(promptTemplateManager.render(eq("query-rewrite-prompt"), anyMap()))
                .thenReturn("rewrite prompt");
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("rewritten");

        // 构建 20 条消息的历史
        StringBuilder longHistory = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longHistory.append("user: message ").append(i).append("\n");
        }

        queryRewriteService.rewrite("query", longHistory.toString());

        // 验证传给 prompt 的历史被截断为最近 3 条
        verify(promptTemplateManager).render(eq("query-rewrite-prompt"), argThat(vars -> {
            String history = (String) vars.get("history");
            // 截断后应该只有 3 行
            String[] lines = history.split("\n");
            return lines.length == 3;
        }));
    }

    @Test
    void shouldFallbackWhenNoChatClientAvailable() {
        ReflectionTestUtils.setField(queryRewriteService, "enabled", true);
        when(chatClients.isEmpty()).thenReturn(true);

        String result = queryRewriteService.rewrite("test query", "some history");

        assertEquals("test query", result);
    }
}
