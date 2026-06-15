package com.example.evimind.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.model.entity.Document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CitationService {

  private final DocumentMapper documentMapper;

  public String generateBibtex(List<Long> documentIds) {
    StringBuilder sb = new StringBuilder();
    for (Long docId : documentIds) {
      Document doc = documentMapper.selectById(docId);
      if (doc == null) continue;
      String key =
          (doc.getFileName() != null ? doc.getFileName().replaceAll("[^a-zA-Z0-9]", "") : "doc")
              + docId;
      sb.append("@article{").append(key).append(",\n");
      sb.append("  title = {")
          .append(doc.getFileName() != null ? doc.getFileName() : "Unknown")
          .append("},\n");
      if (doc.getDoi() != null) sb.append("  doi = {").append(doc.getDoi()).append("},\n");
      if (doc.getPublicationYear() != null)
        sb.append("  year = {").append(doc.getPublicationYear()).append("},\n");
      if (doc.getAuthors() != null)
        sb.append("  author = {").append(doc.getAuthors()).append("},\n");
      sb.append("}\n\n");
    }
    return sb.toString();
  }

  public String generateApa(List<Long> documentIds) {
    StringBuilder sb = new StringBuilder();
    for (Long docId : documentIds) {
      Document doc = documentMapper.selectById(docId);
      if (doc == null) continue;
      if (doc.getAuthors() != null) sb.append(doc.getAuthors()).append(" ");
      if (doc.getPublicationYear() != null)
        sb.append("(").append(doc.getPublicationYear()).append("). ");
      sb.append(doc.getFileName() != null ? doc.getFileName() : "Untitled").append(". ");
      if (doc.getDoi() != null) sb.append("DOI: ").append(doc.getDoi());
      sb.append("\n\n");
    }
    return sb.toString();
  }
}
