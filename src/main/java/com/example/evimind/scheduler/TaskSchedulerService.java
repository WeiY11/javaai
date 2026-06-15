package com.example.evimind.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.ScheduledTaskMapper;
import com.example.evimind.model.entity.ScheduledTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 定时任务调度服务。 使用 Spring @Scheduled 轮询数据库中的 scheduled_task 表， 执行到期任务（知识库重索引、报告生成、数据清理等）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSchedulerService {

  private final ScheduledTaskMapper taskMapper;
  private final TaskExecutorRegistry taskExecutorRegistry;

  /** 每 30 秒扫描一次到期任务并执行。 */
  @Scheduled(fixedDelay = 30000)
  public void pollAndExecuteDueTasks() {
    LocalDateTime now = LocalDateTime.now();
    List<ScheduledTask> dueTasks = taskMapper.findDueTasks(now);

    for (ScheduledTask task : dueTasks) {
      try {
        log.info(
            "Executing scheduled task: {} (id={}, type={})",
            task.getName(),
            task.getId(),
            task.getTaskType());
        executeTask(task);

        task.setLastRunAt(now);
        task.setNextRunAt(calculateNextRun(task.getCronExpression()));
        taskMapper.updateById(task);
      } catch (Exception e) {
        log.error("Scheduled task {} failed: {}", task.getId(), e.getMessage());
        task.setStatus("ERROR");
        taskMapper.updateById(task);
      }
    }
  }

  @Transactional
  public ScheduledTask createTask(
      String name, String cronExpression, String taskType, Map<String, Object> config) {
    ScheduledTask task = new ScheduledTask();
    task.setName(name);
    task.setCronExpression(cronExpression);
    task.setTaskType(taskType);
    task.setConfig(config != null ? toJson(config) : "{}");
    task.setStatus("ACTIVE");
    task.setCreatorId(GroupContext.getUserId());
    task.setNextRunAt(calculateNextRun(cronExpression));
    taskMapper.insert(task);
    log.info("Created scheduled task: {} (type={}, cron={})", name, taskType, cronExpression);
    return task;
  }

  @Transactional
  public void pauseTask(Long taskId) {
    ScheduledTask task = taskMapper.selectById(taskId);
    if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
    task.setStatus("PAUSED");
    taskMapper.updateById(task);
  }

  @Transactional
  public void resumeTask(Long taskId) {
    ScheduledTask task = taskMapper.selectById(taskId);
    if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
    task.setStatus("ACTIVE");
    task.setNextRunAt(calculateNextRun(task.getCronExpression()));
    taskMapper.updateById(task);
  }

  @Transactional
  public void deleteTask(Long taskId) {
    taskMapper.deleteById(taskId);
  }

  @Transactional
  public void runNow(Long taskId) {
    ScheduledTask task = taskMapper.selectById(taskId);
    if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
    executeTask(task);
    task.setLastRunAt(LocalDateTime.now());
    task.setNextRunAt(calculateNextRun(task.getCronExpression()));
    taskMapper.updateById(task);
  }

  public IPage<ScheduledTask> listTasks(int page, int size) {
    return taskMapper.selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<ScheduledTask>().orderByDesc(ScheduledTask::getCreatedAt));
  }

  public List<ScheduledTask> listActiveTasks() {
    return taskMapper.findAllActive();
  }

  private void executeTask(ScheduledTask task) {
    TaskExecutor executor = taskExecutorRegistry.getExecutor(task.getTaskType());
    if (executor == null) {
      log.warn("No executor registered for task type: {}", task.getTaskType());
      return;
    }
    executor.execute(task);
  }

  private LocalDateTime calculateNextRun(String cronExpression) {
    // Simple next-run estimation: add 1 hour as default
    // In production, use a CronExpression parser
    return LocalDateTime.now().plusHours(1);
  }

  private String toJson(Map<String, Object> map) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
    } catch (Exception e) {
      return "{}";
    }
  }
}
