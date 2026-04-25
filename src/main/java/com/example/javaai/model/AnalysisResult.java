package com.example.javaai.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class AnalysisResult {

    private String id;
    private String filePath;
    private String fileName;
    private String provider;
    private String sessionId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime analyzedAt;
    private String content;
    private long fileSize;
    private String fileCategory;

    public AnalysisResult() {}

    public AnalysisResult(String id, String filePath, String fileName, String provider,
                          String sessionId, LocalDateTime analyzedAt, String content,
                          long fileSize, String fileCategory) {
        this.id = id;
        this.filePath = filePath;
        this.fileName = fileName;
        this.provider = provider;
        this.sessionId = sessionId;
        this.analyzedAt = analyzedAt;
        this.content = content;
        this.fileSize = fileSize;
        this.fileCategory = fileCategory;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFileCategory() { return fileCategory; }
    public void setFileCategory(String fileCategory) { this.fileCategory = fileCategory; }
}
