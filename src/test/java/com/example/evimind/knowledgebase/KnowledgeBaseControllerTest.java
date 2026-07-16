package com.example.evimind.knowledgebase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.retrieval.SearchResult;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {

  @Mock private KnowledgeBaseService knowledgeBaseService;

  @InjectMocks private KnowledgeBaseController knowledgeBaseController;

  @Test
  void shouldExposeDirectKnowledgeBaseSearchEndpoint() {
    KnowledgeBaseSearchRequest request = new KnowledgeBaseSearchRequest();
    request.setQuery("DAG scheduling");
    request.setTopK(3);

    List<SearchResult> results =
        List.of(
            new SearchResult(
                "chunk_1", 10L, 7L, "DAG scheduling evidence", 2, 0.92, "rrf_fused"));
    when(knowledgeBaseService.search(eq(7L), same(request))).thenReturn(results);

    ResponseEntity<ApiResponse<List<SearchResult>>> response =
        knowledgeBaseController.search(7L, request);

    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(results, response.getBody().getData());
    verify(knowledgeBaseService).search(7L, request);
  }
}
