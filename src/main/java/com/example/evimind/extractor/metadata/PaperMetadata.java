package com.example.evimind.extractor.metadata;

import java.util.List;

import lombok.Builder;
import lombok.Data;

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
