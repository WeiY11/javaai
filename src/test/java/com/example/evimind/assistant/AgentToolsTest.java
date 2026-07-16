package com.example.evimind.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.knowledgebase.KnowledgeBaseService;
import com.example.evimind.retrieval.SearchResult;

class AgentToolsTest {

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  private AgentTools createAgentTools(KnowledgeBaseService knowledgeBaseService) {
    return new AgentTools(knowledgeBaseService);
  }

  @Test
  void shouldClampKbSearchTopKAndLimitToolOutput() {
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    GroupContext.set(1L, 1L, "USER");
    String longContent = "content ".repeat(1000);

    when(knowledgeBaseService.search(eq(1L), any()))
        .thenReturn(
            List.of(new SearchResult("service", 1L, 1L, longContent, 0, 0.95, "rrf_fused")));
    AgentTools agentTools = createAgentTools(knowledgeBaseService);
    AgentTools.KbSearchResponse response =
        agentTools.kbSearch().apply(new AgentTools.KbSearchRequest("query", 1L, 100));

    assertNull(response.error());
    assertTrue(response.results().length() <= 4000);
    assertTrue(response.results().contains("文档ID=1"));
  }

  @Test
  void shouldRejectKbSearchWithoutAnAuthenticatedUser() {
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    AgentTools agentTools = createAgentTools(knowledgeBaseService);
    GroupContext.clear();

    AgentTools.KbSearchResponse response =
        agentTools.kbSearch().apply(new AgentTools.KbSearchRequest("query", 1L, 5));

    assertNull(response.results());
    assertNotNull(response.error());
    verifyNoInteractions(knowledgeBaseService);
  }

  @Test
  void shouldRejectKbSearchWhenCurrentUserIsNotAMember() {
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    GroupContext.set(2L, 1L, "USER");
    when(knowledgeBaseService.search(eq(9L), any()))
        .thenThrow(new SecurityException("Access denied"));
    AgentTools agentTools = createAgentTools(knowledgeBaseService);

    AgentTools.KbSearchResponse response =
        agentTools.kbSearch().apply(new AgentTools.KbSearchRequest("query", 9L, 5));

    assertNull(response.results());
    assertEquals("Access denied.", response.error());
    verify(knowledgeBaseService).search(eq(9L), any());
  }

  @Test
  void shouldNotExposeInternalSearchFailuresToTheModel() {
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    GroupContext.set(1L, 1L, "USER");
    when(knowledgeBaseService.search(eq(1L), any()))
        .thenThrow(new RuntimeException("database password is confidential"));
    AgentTools agentTools = createAgentTools(knowledgeBaseService);

    AgentTools.KbSearchResponse response =
        agentTools.kbSearch().apply(new AgentTools.KbSearchRequest("query", 1L, 5));

    assertNull(response.results());
    assertEquals("Search failed. Please try again.", response.error());
  }
}
