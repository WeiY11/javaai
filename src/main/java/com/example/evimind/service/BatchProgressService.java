package com.example.evimind.service;

import com.example.evimind.model.BatchProgress;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BatchProgressService {

    private final ConcurrentHashMap<String, BatchProgress> progressMap = new ConcurrentHashMap<>();

    public String createProgress(int totalCount) {
        String taskId = UUID.randomUUID().toString();
        BatchProgress progress = new BatchProgress();
        progress.setTaskId(taskId);
        progress.setTotalCount(totalCount);
        progress.setCompletedCount(0);
        progress.setCurrentFile("");
        progress.setStatus("RUNNING");
        progress.setStartTime(LocalDateTime.now());
        progressMap.put(taskId, progress);
        return taskId;
    }

    public void updateProgress(String taskId, String currentFile, int completedCount) {
        BatchProgress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setCurrentFile(currentFile);
            progress.setCompletedCount(completedCount);
        }
    }

    public void completeProgress(String taskId) {
        BatchProgress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setStatus("COMPLETED");
            progress.setEndTime(LocalDateTime.now());
            progress.setCurrentFile("");
        }
    }

    public void failProgress(String taskId, String error) {
        BatchProgress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setStatus("FAILED");
            progress.setErrorMessage(error);
            progress.setEndTime(LocalDateTime.now());
        }
    }

    public BatchProgress getProgress(String taskId) {
        return progressMap.get(taskId);
    }
}
