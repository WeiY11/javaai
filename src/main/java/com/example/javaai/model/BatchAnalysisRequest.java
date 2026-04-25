package com.example.javaai.model;

import java.util.List;

public class BatchAnalysisRequest {

    private List<String> paths;
    private String provider;
    private String sessionId;

    public BatchAnalysisRequest() {}

    public List<String> getPaths() { return paths; }
    public void setPaths(List<String> paths) { this.paths = paths; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
