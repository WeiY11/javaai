package com.example.evimind.service;

import java.util.List;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.ResearchNoteMapper;
import com.example.evimind.model.entity.ResearchNote;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchNoteService {

  private final ResearchNoteMapper researchNoteMapper;

  @Transactional
  public ResearchNote create(ResearchNote note) {
    note.setUserId(requireAuthenticatedUser());
    researchNoteMapper.insert(note);
    return note;
  }

  @Transactional
  public ResearchNote update(Long id, ResearchNote note) {
    ResearchNote existing = requireOwner(id);
    existing.setContent(note.getContent());
    existing.setHighlight(note.getHighlight());
    existing.setTags(note.getTags());
    researchNoteMapper.updateById(existing);
    return existing;
  }

  @Transactional
  public void delete(Long id) {
    requireOwner(id);
    researchNoteMapper.deleteById(id);
  }

  public List<ResearchNote> listByChunk(Long chunkId) {
    Long userId = requireAuthenticatedUser();
    return researchNoteMapper.selectList(
        new LambdaQueryWrapper<ResearchNote>()
            .eq(ResearchNote::getChunkId, chunkId)
            .eq(ResearchNote::getUserId, userId)
            .orderByDesc(ResearchNote::getCreatedAt));
  }

  public List<ResearchNote> listByDocument(Long documentId) {
    Long userId = requireAuthenticatedUser();
    return researchNoteMapper.selectList(
        new LambdaQueryWrapper<ResearchNote>()
            .eq(ResearchNote::getDocumentId, documentId)
            .eq(ResearchNote::getUserId, userId)
            .orderByDesc(ResearchNote::getCreatedAt));
  }

  private ResearchNote requireOwner(Long id) {
    Long userId = requireAuthenticatedUser();
    ResearchNote note = researchNoteMapper.selectById(id);
    if (note == null) throw new IllegalArgumentException("Research note not found: " + id);
    if (!userId.equals(note.getUserId())) {
      throw new SecurityException("Access denied: you do not own this note");
    }
    return note;
  }

  private Long requireAuthenticatedUser() {
    Long userId = GroupContext.getUserId();
    if (userId == null) {
      throw new AuthenticationCredentialsNotFoundException("Not authenticated");
    }
    return userId;
  }
}
