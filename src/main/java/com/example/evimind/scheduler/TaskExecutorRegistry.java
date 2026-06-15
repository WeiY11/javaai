package com.example.evimind.scheduler;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务执行器注册表。
 * 自动收集所有 TaskExecutor 实现，按 taskType 索引。
 */
@Component
public class TaskExecutorRegistry {

    private final Map<String, TaskExecutor> executors = new HashMap<>();

    public TaskExecutorRegistry(List<TaskExecutor> executorList) {
        for (TaskExecutor executor : executorList) {
            executors.put(executor.getTaskType(), executor);
        }
    }

    public TaskExecutor getExecutor(String taskType) {
        return executors.get(taskType);
    }

    public boolean hasExecutor(String taskType) {
        return executors.containsKey(taskType);
    }
}
