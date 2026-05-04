package com.example.javaai.extractor.metadata;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PaperMetadata {
    private String title;
    private List<String> authors;
    private String abstractText;
    private String doi;
    private Integer year;
    private String journal;
    private List<String> references;
}
