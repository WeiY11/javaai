package com.example.evimind.scheduler;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.evimind.audit.AuditService;
import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.model.entity.ScheduledTask;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

  private final TaskSchedulerService taskSchedulerService;
  private final AuditService auditService;

  @GetMapping
  public ResponseEntity<ApiResponse<IPage<ScheduledTask>>> listTasks(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(ApiResponse.success(taskSchedulerService.listTasks(page, size)));
  }

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<List<ScheduledTask>>> listActiveTasks() {
    return ResponseEntity.ok(ApiResponse.success(taskSchedulerService.listActiveTasks()));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ScheduledTask>> createTask(
      @RequestBody Map<String, Object> body) {
    String name = (String) body.get("name");
    String cronExpression = (String) body.get("cronExpression");
    String taskType = (String) body.get("taskType");
    @SuppressWarnings("unchecked")
    Map<String, Object> config = (Map<String, Object>) body.get("config");

    if (name == null || cronExpression == null || taskType == null) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error(400, "name, cronExpression, and taskType are required"));
    }

    ScheduledTask task = taskSchedulerService.createTask(name, cronExpression, taskType, config);

    auditService.log(
        "SCHEDULER_CREATE",
        "SCHEDULED_TASK",
        task.getId(),
        Map.of("name", name, "taskType", taskType, "cron", cronExpression));

    return ResponseEntity.ok(ApiResponse.success(task));
  }

  @PostMapping("/{taskId}/pause")
  public ResponseEntity<ApiResponse<Void>> pauseTask(@PathVariable Long taskId) {
    taskSchedulerService.pauseTask(taskId);
    auditService.log("SCHEDULER_PAUSE", "SCHEDULED_TASK", taskId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @PostMapping("/{taskId}/resume")
  public ResponseEntity<ApiResponse<Void>> resumeTask(@PathVariable Long taskId) {
    taskSchedulerService.resumeTask(taskId);
    auditService.log("SCHEDULER_RESUME", "SCHEDULED_TASK", taskId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @PostMapping("/{taskId}/run")
  public ResponseEntity<ApiResponse<Void>> runNow(@PathVariable Long taskId) {
    taskSchedulerService.runNow(taskId);
    auditService.log("SCHEDULER_RUN_NOW", "SCHEDULED_TASK", taskId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @DeleteMapping("/{taskId}")
  public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable Long taskId) {
    taskSchedulerService.deleteTask(taskId);
    auditService.log("SCHEDULER_DELETE", "SCHEDULED_TASK", taskId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
