package com.example.evimind.ingestion;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DocumentChunker {

  @Data
  public static class ChunkConfig {
    private int chunkSize = 500;
    private int overlap = 100;
    private ChunkStrategy strategy = ChunkStrategy.PARAGRAPH;
  }

  public enum ChunkStrategy {
    FIXED_LENGTH,
    PARAGRAPH,
    SEMANTIC
  }

  public List<String> chunk(String text, ChunkConfig config) {
    if (text == null || text.isBlank()) {
      return List.of();
    }

    return switch (config.getStrategy()) {
      case FIXED_LENGTH -> chunkFixedLength(text, config);
      case PARAGRAPH -> chunkByParagraph(text, config);
      case SEMANTIC -> chunkSemantic(text, config);
    };
  }

  private List<String> chunkSemantic(String text, ChunkConfig config) {
    List<String> sentences = splitSentences(text);
    List<String> chunks = new ArrayList<>();
    StringBuilder currentChunk = new StringBuilder();

    for (String sentence : sentences) {
      String trimmed = sentence.trim();
      if (trimmed.isEmpty()) continue;

      if (currentChunk.length() + trimmed.length() + 1 > config.getChunkSize()
          && currentChunk.length() > 0) {
        chunks.add(currentChunk.toString().trim());
        String overlapText = getOverlapText(currentChunk.toString(), config.getOverlap());
        currentChunk = new StringBuilder(overlapText);
      }

      if (currentChunk.length() > 0) {
        currentChunk.append(" ");
      }
      currentChunk.append(trimmed);
    }

    if (currentChunk.length() > 0) {
      chunks.add(currentChunk.toString().trim());
    }

    log.debug(
        "Chunked text into {} chunks (strategy=SEMANTIC, size={}, overlap={})",
        chunks.size(),
        config.getChunkSize(),
        config.getOverlap());
    return chunks;
  }

  private List<String> splitSentences(String text) {
    List<String> sentences = new ArrayList<>();
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("[^。！？.!?\\n]+[。！？.!?\\n]*").matcher(text);
    while (matcher.find()) {
      String s = matcher.group().trim();
      if (!s.isEmpty()) sentences.add(s);
    }
    return sentences;
  }

  private List<String> chunkFixedLength(String text, ChunkConfig config) {
    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(start + config.getChunkSize(), text.length());
      chunks.add(text.substring(start, end));
      start = end - config.getOverlap();
      if (start >= text.length()) break;
    }
    return chunks;
  }

  private List<String> chunkByParagraph(String text, ChunkConfig config) {
    List<String> chunks = new ArrayList<>();
    String[] paragraphs = text.split("\\n\\s*\\n");
    StringBuilder currentChunk = new StringBuilder();

    for (String paragraph : paragraphs) {
      String trimmed = paragraph.trim();
      if (trimmed.isEmpty()) continue;

      if (currentChunk.length() + trimmed.length() + 2 > config.getChunkSize()
          && currentChunk.length() > 0) {
        chunks.add(currentChunk.toString().trim());
        String overlapText = getOverlapText(currentChunk.toString(), config.getOverlap());
        currentChunk = new StringBuilder(overlapText);
      }

      if (currentChunk.length() > 0) {
        currentChunk.append("\n\n");
      }
      currentChunk.append(trimmed);
    }

    if (currentChunk.length() > 0) {
      chunks.add(currentChunk.toString().trim());
    }

    log.debug(
        "Chunked text into {} chunks (strategy=PARAGRAPH, size={}, overlap={})",
        chunks.size(),
        config.getChunkSize(),
        config.getOverlap());
    return chunks;
  }

  private String getOverlapText(String text, int overlapSize) {
    if (text.length() <= overlapSize) return text;
    int targetStart = text.length() - overlapSize;

    int breakBefore = -1;
    for (int i = targetStart; i >= 0; i--) {
      char c = text.charAt(i);
      if (c == '.' || c == '。' || c == '!' || c == '！' || c == '?' || c == '？' || c == '\n') {
        breakBefore = i;
        break;
      }
    }

    int breakAfter = -1;
    for (int i = targetStart; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '.' || c == '。' || c == '!' || c == '！' || c == '?' || c == '？' || c == '\n') {
        breakAfter = i;
        break;
      }
    }

    int bestStart = targetStart;
    if (breakBefore != -1 && breakAfter != -1) {
      if ((targetStart - breakBefore) <= (breakAfter - targetStart)) {
        bestStart = breakBefore + 1;
      } else {
        bestStart = breakAfter + 1;
      }
    } else if (breakBefore != -1) {
      bestStart = breakBefore + 1;
    } else if (breakAfter != -1) {
      bestStart = breakAfter + 1;
    }

    if (text.length() - bestStart < Math.max(10, overlapSize * 0.2)) {
      bestStart = targetStart;
    }

    return text.substring(bestStart).trim();
  }
}
