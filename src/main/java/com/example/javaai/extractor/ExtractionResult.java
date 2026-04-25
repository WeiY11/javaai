package com.example.javaai.extractor;

import java.util.Collections;
import java.util.Map;

public class ExtractionResult {

    private final boolean success;
    private final String content;
    private final String contentType;
    private final String errorMessage;
    private final Map<String, Object> metadata;

    private ExtractionResult(boolean success, String content, String contentType,
                             String errorMessage, Map<String, Object> metadata) {
        this.success = success;
        this.content = content;
        this.contentType = contentType;
        this.errorMessage = errorMessage;
        this.metadata = metadata != null ? metadata : Collections.emptyMap();
    }

    public static ExtractionResult success(String content, String contentType, Map<String, Object> metadata) {
        return new ExtractionResult(true, content, contentType, null, metadata);
    }

    public static ExtractionResult failure(String errorMessage) {
        return new ExtractionResult(false, null, null, errorMessage, null);
    }

    public boolean isSuccess() { return success; }
    public String getContent() { return content; }
    public String getContentType() { return contentType; }
    public String getErrorMessage() { return errorMessage; }
    public Map<String, Object> getMetadata() { return metadata; }
}
