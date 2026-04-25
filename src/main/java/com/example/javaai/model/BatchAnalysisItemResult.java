package com.example.javaai.model;

public class BatchAnalysisItemResult {

    private String filePath;
    private String fileName;
    private boolean success;
    private String content;
    private String error;
    private String resultId;

    public BatchAnalysisItemResult() {}

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
}
