package com.example.javaai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "custom.analysis")
public class AnalysisProperties {

    private int maxContentSize = 512000;
    private int maxPromptSize = 100000;
    private int batchMaxFiles = 20;
    private String resultDir = ".analysis-results";
    private String reportDir = ".analysis-reports";
    private String pdfFontPath = "";

    public int getMaxContentSize() { return maxContentSize; }
    public void setMaxContentSize(int maxContentSize) { this.maxContentSize = maxContentSize; }

    public int getMaxPromptSize() { return maxPromptSize; }
    public void setMaxPromptSize(int maxPromptSize) { this.maxPromptSize = maxPromptSize; }

    public int getBatchMaxFiles() { return batchMaxFiles; }
    public void setBatchMaxFiles(int batchMaxFiles) { this.batchMaxFiles = batchMaxFiles; }

    public String getResultDir() { return resultDir; }
    public void setResultDir(String resultDir) { this.resultDir = resultDir; }

    public String getReportDir() { return reportDir; }
    public void setReportDir(String reportDir) { this.reportDir = reportDir; }

    public String getPdfFontPath() { return pdfFontPath; }
    public void setPdfFontPath(String pdfFontPath) { this.pdfFontPath = pdfFontPath; }
}
