package com.example.evimind.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.ResearchNoteMapper;
import com.example.evimind.model.entity.ResearchNote;

class ResearchNoteServiceTest {

  private final ResearchNoteMapper researchNoteMapper = mock(ResearchNoteMapper.class);
  private final ResearchNoteService service = new ResearchNoteService(researchNoteMapper);

  @BeforeEach
  void setUp() {
    GroupContext.set(7L, 1L, "USER");
  }

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void listByChunkShouldScopeNotesToTheCurrentUser() {
    ResearchNote note = new ResearchNote();
    note.setId(4L);
    when(researchNoteMapper.selectList(any())).thenReturn(List.of(note));

    List<ResearchNote> notes = service.listByChunk(9L);

    assertThat(notes).containsExactly(note);
    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<LambdaQueryWrapper<ResearchNote>> wrapperCaptor =
        org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(researchNoteMapper).selectList(wrapperCaptor.capture());
    assertThat(wrapperCaptor.getValue().getExpression().getNormal()).hasSize(7);
  }

  @Test
  void deleteShouldRejectMissingAuthenticationBeforeLoadingTheNote() {
    GroupContext.clear();

    assertThatThrownBy(() -> service.delete(4L))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
        .hasMessageContaining("Not authenticated");

    verifyNoInteractions(researchNoteMapper);
  }
}
