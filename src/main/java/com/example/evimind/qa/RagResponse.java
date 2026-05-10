package com.example.evimind.qa;

import com.example.evimind.retrieval.SearchResult;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RagResponse {

    private String answer;
    private List<Citation> citations;
    private EvidenceStatus evidenceStatus;
    private boolean degradedMode;

    public enum EvidenceStatus {
        SUFFICIENT, INSUFFICIENT, NO_RESULTS
    }

    @Data
    public static class Citation {
        private Long documentId;
        private String fileName;
        private int chunkIndex;
        private double score;
    }
}
