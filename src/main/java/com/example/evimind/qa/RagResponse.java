package com.example.evimind.qa;

import java.util.List;

import lombok.Data;

@Data
public class RagResponse {

  private String answer;
  private List<Citation> citations;
  private EvidenceStatus evidenceStatus;
  private boolean degradedMode;

  public enum EvidenceStatus {
    SUFFICIENT,
    INSUFFICIENT,
    NO_RESULTS
  }

  @Data
  public static class Citation {
    private Long documentId;
    private String fileName;
    private int chunkIndex;
    private double score;
  }
}
