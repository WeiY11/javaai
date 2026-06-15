package com.example.evimind.retrieval;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SearchResult {

  private String chunkId;
  private Long documentId;
  private Long knowledgeBaseId;
  private String content;
  private int chunkIndex;
  private double score;
  private String source;
}
