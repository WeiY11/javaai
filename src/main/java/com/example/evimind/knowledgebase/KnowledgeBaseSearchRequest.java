package com.example.evimind.knowledgebase;

import lombok.Data;

@Data
public class KnowledgeBaseSearchRequest {

  private String query;
  private Integer topK = 10;
  private String conversationHistory;
  private Boolean rerank = true;
}
