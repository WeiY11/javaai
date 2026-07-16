package com.example.evimind.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.ScheduledTaskMapper;

class TaskSchedulerServiceTest {

  private final TaskSchedulerService service =
      new TaskSchedulerService(mock(ScheduledTaskMapper.class), new TaskExecutorRegistry(List.of()));

  @AfterEach
  void tearDown() {
    GroupContext.clear();
  }

  @Test
  void calculateNextRunShouldRespectCronExpression() {
    LocalDateTime next =
        service.calculateNextRun("0 15 9 * * *", LocalDateTime.of(2026, 7, 1, 8, 0));

    assertThat(next).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 15));
  }

  @Test
  void calculateNextRunShouldRejectInvalidCronExpression() {
    assertThatThrownBy(() -> service.calculateNextRun("not cron", LocalDateTime.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createTaskShouldRejectNonAdminBeforePersistingAGlobalTask() {
    ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
    TaskSchedulerService taskSchedulerService =
        new TaskSchedulerService(taskMapper, new TaskExecutorRegistry(List.of()));
    GroupContext.set(7L, 1L, "USER");

    assertThatThrownBy(
            () ->
                taskSchedulerService.createTask(
                    "cleanup", "0 0 0 * * *", "DATA_CLEANUP", java.util.Map.of()))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("administrator");

    verifyNoInteractions(taskMapper);
  }

  @Test
  void listActiveTasksShouldRejectNonAdminBeforeQueryingGlobalTasks() {
    ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
    TaskSchedulerService taskSchedulerService =
        new TaskSchedulerService(taskMapper, new TaskExecutorRegistry(List.of()));
    GroupContext.set(7L, 1L, "USER");

    assertThatThrownBy(taskSchedulerService::listActiveTasks)
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("administrator");

    verifyNoInteractions(taskMapper);
  }
}
