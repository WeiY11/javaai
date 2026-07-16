package com.example.evimind.knowledgebase;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.KbMemberMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.KbMember;
import com.example.evimind.model.entity.KnowledgeBase;
import com.example.evimind.retrieval.HybridSearchService;
import com.example.evimind.retrieval.Reranker;
import com.example.evimind.retrieval.SearchResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final KbMemberMapper kbMemberMapper;

  @Autowired private HybridSearchService hybridSearchService;

  @Autowired(required = false)
  private Reranker reranker;

  @Value("${custom.kb.search.max-top-k:20}")
  private int maxSearchTopK = 20;

  @Value("${custom.kb.search.candidate-multiplier:2}")
  private int searchCandidateMultiplier = 2;

  @Value("${custom.kb.search.reranker.enabled:true}")
  private boolean searchRerankerEnabled = true;

  @Transactional
  public KnowledgeBase create(KnowledgeBase kb) {
    Long userId = requireAuthenticatedUser();
    if (kb.getId() != null && kb.getId() <= 0) kb.setId(null);
    if (kb.getEvidenceThreshold() == null) kb.setEvidenceThreshold(new BigDecimal("0.50"));
    if (kb.getChunkStrategy() == null) kb.setChunkStrategy("PARAGRAPH");
    if (kb.getChunkSize() == null) kb.setChunkSize(500);
    if (kb.getChunkOverlap() == null) kb.setChunkOverlap(100);
    kb.setStatus("ACTIVE");
    kb.setCreatorId(userId);
    kb.setGroupId(GroupContext.getGroupId());
    knowledgeBaseMapper.insert(kb);

    KbMember member = new KbMember();
    member.setKnowledgeBaseId(kb.getId());
    member.setUserId(userId);
    member.setRole("OWNER");
    kbMemberMapper.insert(member);

    log.info("Created knowledge base: {} (id={})", kb.getName(), kb.getId());
    return kb;
  }

  public KnowledgeBase update(KnowledgeBase kb) {
    if (!isOwner(kb.getId())) {
      throw new SecurityException("Only OWNER can update knowledge base");
    }
    knowledgeBaseMapper.updateById(kb);
    return kb;
  }

  @Transactional
  public void delete(Long kbId) {
    if (!isOwner(kbId)) {
      throw new SecurityException("Only OWNER can delete knowledge base");
    }
    knowledgeBaseMapper.deleteById(kbId);
    log.info("Deleted knowledge base id={}", kbId);
  }

  public Page<KnowledgeBase> listAccessible(int page, int size) {
    Long userId = requireAuthenticatedUser();

    List<Long> kbIds =
        kbMemberMapper
            .selectList(new LambdaQueryWrapper<KbMember>().eq(KbMember::getUserId, userId))
            .stream()
            .map(KbMember::getKnowledgeBaseId)
            .collect(Collectors.toList());

    if (kbIds.isEmpty()) {
      return new Page<>(page, size);
    }

    return knowledgeBaseMapper.selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<KnowledgeBase>()
            .in(KnowledgeBase::getId, kbIds)
            .eq(KnowledgeBase::getStatus, "ACTIVE")
            .orderByDesc(KnowledgeBase::getCreatedAt));
  }

  public KnowledgeBase getById(Long kbId) {
    KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
    if (kb != null && !isMember(kbId) && !GroupContext.isAdmin()) {
      throw new SecurityException("Access denied: you are not a member of this knowledge base");
    }
    return kb;
  }

  public List<SearchResult> search(Long kbId, KnowledgeBaseSearchRequest request) {
    String query = normalizeQuery(request);

    KnowledgeBase kb = getById(kbId);
    if (kb == null) {
      throw new IllegalArgumentException("Knowledge base not found: " + kbId);
    }

    int requestedTopK = clampTopK(request.getTopK());
    int candidateTopK = candidateTopK(requestedTopK);
    String conversationHistory = blankToNull(request.getConversationHistory());

    List<SearchResult> candidates =
        hybridSearchService.search(query, kbId, candidateTopK, conversationHistory);
    if (candidates.isEmpty()) {
      return candidates;
    }

    if (shouldRerank(request, candidates)) {
      try {
        return reranker.rerank(query, candidates, requestedTopK).stream()
            .limit(requestedTopK)
            .toList();
      } catch (Exception e) {
        log.warn(
            "Knowledge base search reranker failed, returning hybrid order ({})",
            e.getClass().getSimpleName());
      }
    }

    return candidates.stream().limit(requestedTopK).toList();
  }

  public boolean isOwner(Long kbId) {
    Long userId = requireAuthenticatedUser();
    Long count =
        kbMemberMapper.selectCount(
            new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getKnowledgeBaseId, kbId)
                .eq(KbMember::getUserId, userId)
                .eq(KbMember::getRole, "OWNER"));
    return count > 0;
  }

  public boolean isMember(Long kbId) {
    Long userId = requireAuthenticatedUser();
    Long count =
        kbMemberMapper.selectCount(
            new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getKnowledgeBaseId, kbId)
                .eq(KbMember::getUserId, userId));
    return count > 0;
  }

  @Transactional
  public KbMember addMember(Long kbId, Long userId, String role) {
    if (!isOwner(kbId)) {
      throw new SecurityException("Only OWNER can add members");
    }
    KbMember member = new KbMember();
    member.setKnowledgeBaseId(kbId);
    member.setUserId(userId);
    member.setRole(role != null ? role : "MEMBER");
    kbMemberMapper.insert(member);
    return member;
  }

  @Transactional
  public void removeMember(Long kbId, Long userId) {
    if (!isOwner(kbId)) {
      throw new SecurityException("Only OWNER can remove members");
    }
    kbMemberMapper.delete(
        new LambdaQueryWrapper<KbMember>()
            .eq(KbMember::getKnowledgeBaseId, kbId)
            .eq(KbMember::getUserId, userId));
  }

  public List<KbMember> listMembers(Long kbId) {
    if (!isMember(kbId) && !GroupContext.isAdmin()) {
      throw new SecurityException("Access denied: you are not a member of this knowledge base");
    }
    return kbMemberMapper.selectList(
        new LambdaQueryWrapper<KbMember>().eq(KbMember::getKnowledgeBaseId, kbId));
  }

  private String normalizeQuery(KnowledgeBaseSearchRequest request) {
    if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
      throw new IllegalArgumentException("Search query must not be blank");
    }
    return request.getQuery().trim();
  }

  private int clampTopK(Integer topK) {
    int safeMax = Math.max(1, maxSearchTopK);
    int requested = topK == null ? 10 : topK;
    return Math.max(1, Math.min(requested, safeMax));
  }

  private int candidateTopK(int requestedTopK) {
    int multiplier = Math.max(1, searchCandidateMultiplier);
    return Math.max(requestedTopK, Math.min(50, requestedTopK * multiplier));
  }

  private boolean shouldRerank(
      KnowledgeBaseSearchRequest request, List<SearchResult> candidates) {
    return searchRerankerEnabled
        && !Boolean.FALSE.equals(request.getRerank())
        && reranker != null
        && candidates.size() > 1;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private Long requireAuthenticatedUser() {
    Long userId = GroupContext.getUserId();
    if (userId == null) {
      throw new AuthenticationCredentialsNotFoundException("Not authenticated");
    }
    return userId;
  }
}
