package com.example.evimind.extractor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.documentnode.epub4j.domain.Author;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;

@Component
public class EpubExtractor implements FileContentExtractor {

  @Override
  public boolean supports(String fileName) {
    return fileName.toLowerCase().endsWith(".epub");
  }

  @Override
  public ExtractionResult extract(Path filePath, int maxSize) throws IOException {
    try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
      EpubReader epubReader = new EpubReader();
      Book book = epubReader.readEpub(fis);

      StringBuilder sb = new StringBuilder();

      // 书籍元数据
      String title = book.getTitle();
      List<Author> authors = book.getMetadata().getAuthors();
      if (title != null && !title.isBlank()) {
        sb.append("# ").append(title).append("\n\n");
      }
      if (authors != null && !authors.isEmpty()) {
        sb.append("作者: ");
        for (int i = 0; i < authors.size(); i++) {
          if (i > 0) sb.append(", ");
          Author author = authors.get(i);
          sb.append(author.getFirstname()).append(" ").append(author.getLastname());
        }
        sb.append("\n\n");
      }

      // 遍历内容
      List<Resource> contents = book.getContents();
      int chapterCount = 0;

      for (Resource resource : contents) {
        String resourceTitle = resource.getTitle();
        if (resourceTitle == null || resourceTitle.isBlank()) {
          resourceTitle = resource.getId();
        }

        byte[] data = resource.getData();
        String html = new String(data, StandardCharsets.UTF_8);
        String text = html.replaceAll("<[^>]+>", "").trim();

        if (text.isEmpty()) continue;

        sb.append("## ").append(resourceTitle).append("\n\n");
        sb.append(text).append("\n\n");
        chapterCount++;
      }

      String content = sb.toString();
      if (content.length() > maxSize) {
        content = content.substring(0, maxSize) + "\n... [内容已截断，文件过大]";
      }

      Map<String, Object> metadata = new HashMap<>();
      metadata.put("chapterCount", chapterCount);
      metadata.put("title", title != null ? title : "");
      metadata.put("authorCount", authors != null ? authors.size() : 0);

      return ExtractionResult.success(content, "text", metadata);

    } catch (IOException e) {
      return ExtractionResult.failure("EPUB文件损坏，无法解析: " + e.getMessage());
    }
  }
}
