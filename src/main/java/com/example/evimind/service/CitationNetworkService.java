package com.example.evimind.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.mapper.CitationLinkMapper;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.model.entity.CitationLink;
import com.example.evimind.model.entity.Document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CitationNetworkService {

  private final CitationLinkMapper citationLinkMapper;
  private final DocumentMapper documentMapper;

  // DOI pattern: 10.XXXX/...
  private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,}/\\S+");
  // Year pattern: 4-digit year between 1900 and 2099
  private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
  // References section header
  private static final Pattern REFERENCES_HEADER_PATTERN =
      Pattern.compile(
          "(?i)^\\s*(references|bibliography|works\\s+cited|文献|参考文献)\\s*$", Pattern.MULTILINE);
  // Individual reference line (numbered or bullet)
  private static final Pattern REFERENCE_LINE_PATTERN =
      Pattern.compile("^\\s*(?:\\[?\\d+\\]?\\.?|[-*])\\s+(.+)$", Pattern.MULTILINE);

  /**
   * 从论文原文中提取参考文献并保存为引用关系。
   *
   * @param documentId 文档 ID
   * @param rawText 文档全文
   * @param knowledgeBaseId 知识库 ID
   * @return 保存的引用链接数量
   */
  @Transactional
  public int extractAndSaveCitations(Long documentId, String rawText, Long knowledgeBaseId) {
    if (rawText == null || rawText.isBlank()) {
      log.warn("Cannot extract citations: raw text is empty for document {}", documentId);
      return 0;
    }

    // 定位 References 段落
    String referencesText = extractReferencesSection(rawText);
    if (referencesText == null || referencesText.isBlank()) {
      log.info("No references section found for document {}", documentId);
      return 0;
    }

    // 拆分单条参考文献
    List<String> individualRefs = splitReferences(referencesText);
    if (individualRefs.isEmpty()) {
      log.info("No individual references parsed for document {}", documentId);
      return 0;
    }

    // 先清除该文档的旧引用记录，避免重复
    citationLinkMapper.delete(
        new LambdaQueryWrapper<CitationLink>().eq(CitationLink::getDocumentId, documentId));

    List<CitationLink> links = new ArrayList<>();
    for (String ref : individualRefs) {
      CitationLink link = parseReference(ref, documentId, knowledgeBaseId);
      if (link != null) {
        links.add(link);
      }
    }

    // 批量插入
    for (CitationLink link : links) {
      citationLinkMapper.insert(link);
    }

    log.info("Extracted and saved {} citation links for document {}", links.size(), documentId);
    return links.size();
  }

  /**
   * 删除指定文档的所有引用链接。
   *
   * @param documentId 文档 ID
   * @return 删除的引用链接数量
   */
  public int deleteCitationsForDocument(Long documentId) {
    return citationLinkMapper.delete(
        new LambdaQueryWrapper<CitationLink>().eq(CitationLink::getDocumentId, documentId));
  }

  /**
   * 获取指定文档的引用列表。
   *
   * @param documentId 文档 ID
   * @return 该文档的引用链接列表
   */
  public List<CitationLink> getCitationsForDocument(Long documentId) {
    return citationLinkMapper.findByDocumentId(documentId);
  }

  /**
   * 构建引用网络图数据。
   *
   * @param knowledgeBaseId 知识库 ID
   * @return 包含 nodes 和 edges 的图结构
   */
  public Map<String, Object> getCitationGraph(Long knowledgeBaseId) {
    List<CitationLink> allLinks = citationLinkMapper.findByKnowledgeBaseId(knowledgeBaseId);
    List<Document> documents =
        documentMapper.selectList(
            new LambdaQueryWrapper<Document>().eq(Document::getKnowledgeBaseId, knowledgeBaseId));

    // 构建节点集合
    Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();

    // 添加文档节点（来源节点）
    for (Document doc : documents) {
      String nodeId = "doc-" + doc.getId();
      Map<String, Object> node = new LinkedHashMap<>();
      node.put("id", nodeId);
      node.put("label", doc.getFileName() != null ? doc.getFileName() : "Document " + doc.getId());
      node.put("type", "document");
      if (doc.getDoi() != null) node.put("doi", doc.getDoi());
      if (doc.getAuthors() != null) node.put("authors", doc.getAuthors());
      if (doc.getPublicationYear() != null) node.put("year", doc.getPublicationYear());
      nodeMap.put(nodeId, node);
    }

    // 添加被引用文献节点 + 边
    List<Map<String, Object>> edges = new ArrayList<>();
    for (CitationLink link : allLinks) {
      String sourceId = "doc-" + link.getDocumentId();

      // 被引用文献节点：优先用 DOI 标识，否则用标题哈希
      String targetId;
      String targetLabel;
      if (link.getCitedDoi() != null && !link.getCitedDoi().isBlank()) {
        targetId = "doi-" + link.getCitedDoi();
        targetLabel = link.getCitedDoi();
      } else if (link.getCitedTitle() != null && !link.getCitedTitle().isBlank()) {
        targetId = "ref-" + Math.abs(link.getCitedTitle().hashCode());
        targetLabel =
            link.getCitedTitle().length() > 60
                ? link.getCitedTitle().substring(0, 60) + "..."
                : link.getCitedTitle();
      } else {
        targetId = "ref-" + link.getId();
        targetLabel = "Reference " + link.getId();
      }

      if (!nodeMap.containsKey(targetId)) {
        Map<String, Object> targetNode = new LinkedHashMap<>();
        targetNode.put("id", targetId);
        targetNode.put("label", targetLabel);
        targetNode.put("type", "cited");
        if (link.getCitedDoi() != null) targetNode.put("doi", link.getCitedDoi());
        if (link.getCitedAuthors() != null) targetNode.put("authors", link.getCitedAuthors());
        if (link.getCitedYear() != null) targetNode.put("year", link.getCitedYear());
        nodeMap.put(targetId, targetNode);
      }

      Map<String, Object> edge = new LinkedHashMap<>();
      edge.put("source", sourceId);
      edge.put("target", targetId);
      edge.put("label", "cites");
      edges.add(edge);
    }

    Map<String, Object> graph = new LinkedHashMap<>();
    graph.put("nodes", new ArrayList<>(nodeMap.values()));
    graph.put("edges", edges);
    return graph;
  }

  /**
   * 获取与给定文档共被引的文档列表。
   *
   * @param documentId 文档 ID
   * @return 共被引文档信息列表
   */
  public List<Map<String, Object>> getCoCitedDocuments(Long documentId) {
    Document doc = documentMapper.selectById(documentId);
    if (doc == null) {
      throw new IllegalArgumentException("Document not found: " + documentId);
    }

    List<CitationLink> coCitations =
        citationLinkMapper.findCoCitations(documentId, doc.getKnowledgeBaseId());

    // 按文档 ID 分组
    Map<Long, List<CitationLink>> grouped =
        coCitations.stream().collect(Collectors.groupingBy(CitationLink::getDocumentId));

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map.Entry<Long, List<CitationLink>> entry : grouped.entrySet()) {
      Document coDoc = documentMapper.selectById(entry.getKey());
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("documentId", entry.getKey());
      item.put("fileName", coDoc != null ? coDoc.getFileName() : "Unknown");
      item.put("sharedCitations", entry.getValue().size());

      List<Map<String, String>> sharedRefs =
          entry.getValue().stream()
              .map(
                  cl -> {
                    Map<String, String> ref = new LinkedHashMap<>();
                    if (cl.getCitedDoi() != null) ref.put("doi", cl.getCitedDoi());
                    if (cl.getCitedTitle() != null) ref.put("title", cl.getCitedTitle());
                    return ref;
                  })
              .collect(Collectors.toList());
      item.put("sharedReferences", sharedRefs);
      result.add(item);
    }

    // 按共享引用数量降序
    result.sort(
        (a, b) -> Integer.compare((int) b.get("sharedCitations"), (int) a.get("sharedCitations")));

    return result;
  }

  /**
   * 获取知识库的引用统计信息。
   *
   * @param knowledgeBaseId 知识库 ID
   * @return 统计数据
   */
  public Map<String, Object> getCitationStats(Long knowledgeBaseId) {
    List<CitationLink> allLinks = citationLinkMapper.findByKnowledgeBaseId(knowledgeBaseId);

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalCitationLinks", allLinks.size());

    // 按文档统计引用数
    Map<Long, Long> citationsPerDoc =
        allLinks.stream()
            .collect(Collectors.groupingBy(CitationLink::getDocumentId, Collectors.counting()));
    stats.put("documentsWithCitations", citationsPerDoc.size());

    // 最常引用的 DOI（top 10）
    Map<String, Long> doiFrequency =
        allLinks.stream()
            .filter(cl -> cl.getCitedDoi() != null && !cl.getCitedDoi().isBlank())
            .collect(Collectors.groupingBy(CitationLink::getCitedDoi, Collectors.counting()));

    List<Map<String, Object>> mostCitedDois =
        doiFrequency.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .map(
                entry -> {
                  Map<String, Object> item = new LinkedHashMap<>();
                  item.put("doi", entry.getKey());
                  item.put("citationCount", entry.getValue());
                  // 找到该 DOI 对应的标题和作者
                  allLinks.stream()
                      .filter(cl -> entry.getKey().equals(cl.getCitedDoi()))
                      .findFirst()
                      .ifPresent(
                          cl -> {
                            if (cl.getCitedTitle() != null) item.put("title", cl.getCitedTitle());
                            if (cl.getCitedAuthors() != null)
                              item.put("authors", cl.getCitedAuthors());
                            if (cl.getCitedYear() != null) item.put("year", cl.getCitedYear());
                          });
                  return item;
                })
            .collect(Collectors.toList());

    stats.put("mostCitedDois", mostCitedDois);

    // 年份分布
    Map<Integer, Long> yearDistribution =
        allLinks.stream()
            .filter(cl -> cl.getCitedYear() != null)
            .collect(Collectors.groupingBy(CitationLink::getCitedYear, Collectors.counting()));
    Map<Integer, Long> sortedYearDist =
        yearDistribution.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    stats.put("yearDistribution", sortedYearDist);

    // 有 DOI 的引用占比
    long withDoi =
        allLinks.stream()
            .filter(cl -> cl.getCitedDoi() != null && !cl.getCitedDoi().isBlank())
            .count();
    stats.put("citationsWithDoi", withDoi);
    stats.put("citationsWithoutDoi", allLinks.size() - withDoi);

    return stats;
  }

  // ==================== 内部方法 ====================

  /** 从全文中提取 References 段落。 */
  private String extractReferencesSection(String rawText) {
    Matcher headerMatcher = REFERENCES_HEADER_PATTERN.matcher(rawText);
    if (headerMatcher.find()) {
      // 从 References 标题之后开始截取
      int start = headerMatcher.end();
      // 截取到文末或下一个一级标题
      String remaining = rawText.substring(start);
      // 尝试找到下一个一级标题作为截断点
      Pattern nextSection =
          Pattern.compile("(?m)^\\s*(?:\\d+\\.\\s+[A-Z]|Appendix|Acknowledgment|Acknowledge)");
      Matcher nextMatcher = nextSection.matcher(remaining);
      if (nextMatcher.find()) {
        return remaining.substring(0, nextMatcher.start());
      }
      return remaining;
    }
    // 如果找不到 References 标题，尝试使用文末最后 30% 的内容
    int lastPortion = (int) (rawText.length() * 0.7);
    String tail = rawText.substring(lastPortion);
    if (DOI_PATTERN.matcher(tail).find()) {
      return tail;
    }
    return null;
  }

  /** 将 References 段落拆分为单条参考文献。 */
  private List<String> splitReferences(String referencesText) {
    List<String> refs = new ArrayList<>();

    // 尝试按编号拆分（[1], [2], ... 或 1., 2., ...）
    Matcher lineMatcher = REFERENCE_LINE_PATTERN.matcher(referencesText);
    while (lineMatcher.find()) {
      String ref = lineMatcher.group(1).trim();
      if (!ref.isBlank()) {
        refs.add(ref);
      }
    }

    // 如果编号拆分失败，按空行或换行拆分
    if (refs.isEmpty()) {
      String[] lines = referencesText.split("\\n\\s*\\n|\\n");
      for (String line : lines) {
        String trimmed = line.trim();
        if (!trimmed.isBlank() && trimmed.length() > 10) {
          refs.add(trimmed);
        }
      }
    }

    return refs;
  }

  /** 解析单条参考文献文本，提取 DOI、标题、作者、年份。 */
  private CitationLink parseReference(String refText, Long documentId, Long knowledgeBaseId) {
    CitationLink link = new CitationLink();
    link.setDocumentId(documentId);
    link.setKnowledgeBaseId(knowledgeBaseId);
    link.setRawReference(refText.length() > 2000 ? refText.substring(0, 2000) : refText);

    // 提取 DOI
    Matcher doiMatcher = DOI_PATTERN.matcher(refText);
    if (doiMatcher.find()) {
      String doi = doiMatcher.group();
      // 清理 DOI 末尾的标点
      doi = doi.replaceAll("[.,;:)]$", "");
      link.setCitedDoi(doi);
    }

    // 提取年份
    Matcher yearMatcher = YEAR_PATTERN.matcher(refText);
    if (yearMatcher.find()) {
      try {
        link.setCitedYear(Integer.parseInt(yearMatcher.group()));
      } catch (NumberFormatException ignored) {
        // 无法解析年份则跳过
      }
    }

    // 提取作者：通常在引用开头，格式为 "Last, F. I." 或 "Last, F. I. & Last2, F. I."
    // 简单启发式：取第一个逗号或句号前的部分
    String authorCandidate = extractAuthors(refText);
    if (authorCandidate != null) {
      link.setCitedAuthors(authorCandidate);
    }

    // 提取标题：启发式 — 在年份后面、DOI 前面的部分
    String titleCandidate = extractTitle(refText, link.getCitedYear());
    if (titleCandidate != null) {
      link.setCitedTitle(titleCandidate);
    }

    // 至少要解析出一些信息才保存
    if (link.getCitedDoi() == null && link.getCitedTitle() == null && link.getCitedYear() == null) {
      // 仍然保存原始引用
      log.debug(
          "Could not parse structured data from reference: {}",
          refText.substring(0, Math.min(100, refText.length())));
    }

    return link;
  }

  /** 启发式提取作者信息。 */
  private String extractAuthors(String refText) {
    // 匹配常见格式: "Author1, A., Author2, B. (2020)" 或 "Author1, A. & Author2, B."
    Pattern authorPattern =
        Pattern.compile(
            "^([A-Z][a-zA-Z]+(?:[,\\s]+[A-Z]\\.?)*(?:\\s*[&,]\\s*[A-Z][a-zA-Z]+(?:[,\\s]+[A-Z]\\.?)*)*)");
    Matcher matcher = authorPattern.matcher(refText);
    if (matcher.find()) {
      String authors = matcher.group(1).trim();
      if (authors.length() > 2 && authors.length() < 500) {
        return authors;
      }
    }
    return null;
  }

  /** 启发式提取论文标题。 */
  private String extractTitle(String refText, Integer year) {
    String working = refText;

    // 如果找到了年份，标题通常在年份之后
    if (year != null) {
      int yearIdx = refText.indexOf(String.valueOf(year));
      if (yearIdx >= 0) {
        // 跳过年份和后面的标点
        int afterYear = yearIdx + 4;
        if (afterYear < refText.length()) {
          working = refText.substring(afterYear).replaceAll("^[.\\s)]+", "").trim();
        }
      }
    }

    // 截取到 DOI 之前
    Matcher doiMatcher = DOI_PATTERN.matcher(working);
    if (doiMatcher.find()) {
      working = working.substring(0, doiMatcher.start()).trim();
    }

    // 截取到句号、引号结束处
    int endIdx = working.length();
    int dotIdx = working.indexOf('.');
    if (dotIdx > 5 && dotIdx < endIdx) {
      endIdx = dotIdx;
    }

    String title = working.substring(0, endIdx).trim().replaceAll("[.\\s]+$", "");
    if (title.length() > 10 && title.length() < 500) {
      return title;
    }
    return null;
  }
}
