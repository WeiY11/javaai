package com.example.javaai.controller;

import com.example.javaai.config.AnalysisProperties;
import com.example.javaai.extractor.ExtractionResult;
import com.example.javaai.model.*;
import com.example.javaai.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Value("${custom.data.base-dir}")
    private String baseDir;

    private final FileExtractorService fileExtractorService;
    private final ChatService chatService;
    private final AnalysisResultService analysisResultService;
    private final BatchProgressService batchProgressService;
    private final ReportExportService reportExportService;
    private final AnalysisProperties analysisProperties;

    private final ConcurrentHashMap<String, List<BatchAnalysisItemResult>> batchResults = new ConcurrentHashMap<>();

    public AnalysisController(FileExtractorService fileExtractorService,
                               ChatService chatService,
                               AnalysisResultService analysisResultService,
                               BatchProgressService batchProgressService,
                               ReportExportService reportExportService,
                               AnalysisProperties analysisProperties) {
        this.fileExtractorService = fileExtractorService;
        this.chatService = chatService;
        this.analysisResultService = analysisResultService;
        this.batchProgressService = batchProgressService;
        this.reportExportService = reportExportService;
        this.analysisProperties = analysisProperties;
    }

    /**
     * 安全校验
     */
    private Path safePath(String relativePath) throws IOException {
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath == null ? "" : relativePath).toAbsolutePath().normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Path traversal detected: " + relativePath);
        }
        return target;
    }

    // ==================== 批量分析 ====================

    @PostMapping("/batch")
    public Map<String, String> startBatchAnalysis(@RequestBody BatchAnalysisRequest request) throws IOException {
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            throw new IllegalArgumentException("文件路径列表不能为空");
        }
        if (request.getPaths().size() > analysisProperties.getBatchMaxFiles()) {
            throw new IllegalArgumentException("单次批量分析文件数不能超过" + analysisProperties.getBatchMaxFiles());
        }

        // 安全校验所有路径
        for (String path : request.getPaths()) {
            safePath(path);
        }

        String provider = request.getProvider() != null ? request.getProvider() : "deepseek";
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "batch-" + UUID.randomUUID();

        String taskId = batchProgressService.createProgress(request.getPaths().size());
        batchResults.put(taskId, new ArrayList<>());

        // 异步执行批量分析
        CompletableFuture.runAsync(() -> {
            List<BatchAnalysisItemResult> results = new ArrayList<>();
            int completed = 0;

            for (String filePath : request.getPaths()) {
                BatchAnalysisItemResult itemResult = new BatchAnalysisItemResult();
                itemResult.setFilePath(filePath);

                try {
                    Path target = safePath(filePath);
                    File file = target.toFile();
                    itemResult.setFileName(file.getName());

                    if (!file.exists() || !file.isFile()) {
                        itemResult.setSuccess(false);
                        itemResult.setError("文件不存在");
                        results.add(itemResult);
                        completed++;
                        batchProgressService.updateProgress(taskId, file.getName(), completed);
                        continue;
                    }

                    // 提取内容
                    ExtractionResult extraction = fileExtractorService.extractFile(
                        target, analysisProperties.getMaxPromptSize());

                    if (!extraction.isSuccess()) {
                        itemResult.setSuccess(false);
                        itemResult.setError("无法提取文件内容: " + extraction.getErrorMessage());
                        results.add(itemResult);
                        completed++;
                        batchProgressService.updateProgress(taskId, file.getName(), completed);
                        continue;
                    }

                    String content = extraction.getContent();
                    if (content.length() > analysisProperties.getMaxPromptSize()) {
                        content = content.substring(0, analysisProperties.getMaxPromptSize()) + "\n... [内容已截断]";
                    }

                    String prompt = String.format(
                        "你是一个数据分析专家。请分析以下实验数据文件 \"%s\" 的内容，给出：\n" +
                        "1. **数据概览**：文件包含了什么数据，有哪些关键字段\n" +
                        "2. **关键发现**：从数据中发现的重要指标和规律\n" +
                        "3. **问题诊断**：可能存在的问题\n" +
                        "4. **优化建议**：基于数据给出参数调优或策略改进建议\n\n" +
                        "文件内容如下：\n```\n%s\n```",
                        file.getName(), content
                    );

                    // 同步收集流式结果
                    String analysisContent = chatService.streamChat(provider, prompt, sessionId)
                        .collectList()
                        .block()
                        .stream()
                        .reduce("", (a, b) -> a + b);

                    // 保存结果
                    String resultId = UUID.randomUUID().toString();
                    AnalysisResult analysisResult = new AnalysisResult(
                        resultId, filePath, file.getName(), provider, sessionId,
                        LocalDateTime.now(), analysisContent, file.length(),
                        getFileCategory(file.getName())
                    );
                    analysisResultService.saveResult(analysisResult);

                    itemResult.setSuccess(true);
                    itemResult.setContent(analysisContent);
                    itemResult.setResultId(resultId);

                } catch (Exception e) {
                    itemResult.setSuccess(false);
                    itemResult.setError("分析失败: " + e.getMessage());
                }

                results.add(itemResult);
                completed++;
                batchProgressService.updateProgress(taskId, itemResult.getFileName(), completed);
            }

            batchResults.put(taskId, results);
            batchProgressService.completeProgress(taskId);
        });

        return Map.of("taskId", taskId);
    }

    @GetMapping("/batch/progress")
    public BatchProgress getBatchProgress(@RequestParam("taskId") String taskId) {
        BatchProgress progress = batchProgressService.getProgress(taskId);
        if (progress == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return progress;
    }

    @GetMapping("/batch/result")
    public Map<String, Object> getBatchResult(@RequestParam("taskId") String taskId) {
        List<BatchAnalysisItemResult> results = batchResults.get(taskId);
        if (results == null) {
            return Map.of("results", Collections.emptyList(), "status", "PENDING");
        }
        BatchProgress progress = batchProgressService.getProgress(taskId);
        String status = progress != null ? progress.getStatus() : "UNKNOWN";
        return Map.of("results", results, "status", status);
    }

    // ==================== 结果查询 ====================

    @GetMapping("/results")
    public Map<String, Object> getAllResults(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return analysisResultService.getAllResults(page, size);
    }

    @GetMapping("/results/file")
    public List<AnalysisResult> getResultsByFile(@RequestParam("path") String filePath) {
        return analysisResultService.getResultsByFile(filePath);
    }

    // ==================== 报告导出 ====================

    @GetMapping("/export/markdown")
    public ResponseEntity<InputStreamResource> exportMarkdown(
            @RequestParam("resultIds") String resultIds,
            @RequestParam(value = "title", defaultValue = "分析报告") String title) throws IOException {

        List<String> ids = Arrays.asList(resultIds.split(","));
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<AnalysisResult> results = analysisResultService.getResultsByIds(ids);
        String relativePath = reportExportService.exportMarkdown(results, title);

        Path filePath = Paths.get(baseDir, relativePath);
        File file = filePath.toFile();
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filePath.getFileName() + "\"")
            .body(resource);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<InputStreamResource> exportPdf(
            @RequestParam("resultIds") String resultIds,
            @RequestParam(value = "title", defaultValue = "分析报告") String title) throws IOException {

        List<String> ids = Arrays.asList(resultIds.split(","));
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<AnalysisResult> results = analysisResultService.getResultsByIds(ids);
        String relativePath = reportExportService.exportPdf(results, title);

        Path filePath = Paths.get(baseDir, relativePath);
        File file = filePath.toFile();
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filePath.getFileName() + "\"")
            .body(resource);
    }

    private String getFileCategory(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".csv")) return "csv";
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return "excel";
        if (lower.endsWith(".docx")) return "word";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".tex")) return "latex";
        if (lower.endsWith(".png") || lower.endsWith(".jpg")) return "image";
        return "other";
    }
}
