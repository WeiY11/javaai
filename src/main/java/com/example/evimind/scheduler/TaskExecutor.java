package com.example.evimind.scheduler;

import com.example.evimind.model.entity.ScheduledTask;

/**
 * 定时任务执行器接口。
 * 每种任务类型（KB_REINDEX、REPORT_GENERATION、DATA_CLEANUP）对应一个实现。
 */
public interface TaskExecutor {
    String getTaskType();
    void execute(ScheduledTask task);
}
