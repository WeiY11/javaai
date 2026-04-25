package com.example.javaai.controller;

import com.example.javaai.config.AnalysisProperties;
import com.example.javaai.extractor.ExtractionResult;
import com.example.javaai.model.AnalysisResult;
import com.example.javaai.service.AnalysisResultService;
import com.example.javaai.service.ChatService;
import com.example.javaai.service.FileExtractorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${custom.data.base-dir}")
    private String baseDir;

    private final ChatService chatService;
    private final FileExtractorService fileExtractorService;
    private final AnalysisResultService analysisResultService;
    private final AnalysisProperties analysisProperties;

    public FileController(ChatService chatService,
                          FileExtractorService fileExtractorService,
                          AnalysisResultService analysisResultService,
                          AnalysisProperties analysisProperties) {
        this.chatService = chatService;
        this.fileExtractorService = fileExtractorService;
        this.analysisResultService = analysisResultService;
        this.analysisProperties = analysisProperties;
    }

    /**
     * 安全校验：确保路径不会跳出 baseDir
     */
    private Path safePath(String relativePath) throws IOException {
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath == null ? "" : relativePath).toAbsolutePath().normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Path traversal detected: " + relativePath);
        }
        return target;
    }

    /**
     * 列出指定目录下的文件和子目录
     */
    @GetMapping
    public Map<String, Object> listFiles(@RequestParam(value = "dir", defaultValue = "") String dir) throws IOException {
        Path dirPath = safePath(dir);
        File folder = dirPath.toFile();

        if (!folder.exists() || !folder.isDirectory()) {
            return Map.of("error", "Directory not found", "path", dir);
        }

        File[] children = folder.listFiles();
        List<Map<String, Object>> items = new ArrayList<>();

        if (children != null) {
            for (File f : children) {
                if (f.getName().startsWith(".") || f.getName().equals("__pycache__")) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", f.getName());
                item.put("isDir", f.isDirectory());
                item.put("size", f.isFile() ? f.length() : 0);
                item.put("lastModified", f.lastModified());
                item.put("path", Paths.get(baseDir).toAbsolutePath().normalize()
                        .relativize(f.toPath().toAbsolutePath().normalize()).toString().replace("\\", "/"));

                if (f.isDirectory()) {
                    item.put("category", "folder");
                } else {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".json")) item.put("category", "json");
                    else if (name.endsWith(".csv")) item.put("category", "csv");
                    else if (name.endsWith(".png") || name.endsWith(".jpg")) item.put("category", "image");
                    else if (name.endsWith(".pt") || name.endsWith(".pth")) item.put("category", "model");
                    else if (name.endsWith(".tex")) item.put("category", "latex");
                    else if (name.endsWith(".md")) item.put("category", "markdown");
                    else if (name.endsWith(".py")) item.put("category", "python");
                    else if (name.endsWith(".pdf")) item.put("category", "pdf");
                    else if (name.endsWith(".xlsx") || name.endsWith(".xls")) item.put("category", "excel");
                    else if (name.endsWith(".docx")) item.put("category", "word");
                    else item.put("category", "other");
                }
                items.add(item);
            }
        }

        items.sort((a, b) -> {
            boolean aDir = (boolean) a.get("isDir");
            boolean bDir = (boolean) b.get("isDir");
            if (aDir != bDir) return aDir ? -1 : 1;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });

        return Map.of(
                "currentDir", dir,
                "items", items
        );
    }

    /**
     * 读取文件内容
     */
    @GetMapping("/content")
    public Map<String, Object> getFileContent(@RequestParam("path") String filePath) throws IOException {
        Path target = safePath(filePath);
        File file = target.toFile();

        if (!file.exists() || !file.isFile()) {
            return Map.of("error", "File not found");
        }

        String name = file.getName().toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", file.getName());
        result.put("size", file.length());
        result.put("path", filePath);

        // 图片文件
        if (name.endsWith(".png") || name.endsWith(".jpg")) {
            byte[] bytes = Files.readAllBytes(target);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mime = name.endsWith(".png") ? "image/png" : "image/jpeg";
            result.put("type", "image");
            result.put("content", "data:" + mime + ";base64," + base64);
            return result;
        }

        // 纯二进制文件
        if (name.endsWith(".pt") || name.endsWith(".pth")) {
            result.put("type", "binary");
            result.put("content", "[二进制文件，大小: " + formatSize(file.length()) + "]");
            return result;
        }

        // 使用提取器服务
        ExtractionResult extraction = fileExtractorService.extractFile(target, analysisProperties.getMaxContentSize());
        if (extraction.isSuccess()) {
            if ("image-base64".equals(extraction.getContentType())) {
                result.put("type", "image");
            } else if ("structured-text".equals(extraction.getContentType())) {
                result.put("type", "structured");
            } else {
                if (name.endsWith(".json")) result.put("type", "json");
                else if (name.endsWith(".csv")) result.put("type", "csv");
                else if (name.endsWith(".md")) result.put("type", "markdown");
                else if (name.endsWith(".pdf")) result.put("type", "pdf");
                else if (name.endsWith(".xlsx") || name.endsWith(".xls")) result.put("type", "excel");
                else if (name.endsWith(".docx")) result.put("type", "word");
                else result.put("type", "text");
            }
            result.put("content", extraction.getContent());
            if (extraction.getMetadata() != null && !extraction.getMetadata().isEmpty()) {
                result.put("metadata", extraction.getMetadata());
            }
        } else {
            result.put("type", "error");
            result.put("content", extraction.getErrorMessage());
        }

        return result;
    }

    /**
     * AI 分析文件内容（流式返回）
     */
    @GetMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> analyzeFile(
            @RequestParam("path") String filePath,
            @RequestParam(value = "provider", defaultValue = "deepseek") String provider,
            @RequestParam(value = "sessionId", defaultValue = "analyze-session") String sessionId) throws IOException {

        Path target = safePath(filePath);
        File file = target.toFile();

        if (!file.exists() || !file.isFile()) {
            return Flux.just("Error: file not found");
        }

        String name = file.getName().toLowerCase();

        // 纯二进制文件无法分析
        if (name.endsWith(".pt") || name.endsWith(".pth")) {
            return Flux.just("该文件为二进制模型文件，无法直接进行文本分析。");
        }

        // 使用提取器获取内容
        ExtractionResult extraction = fileExtractorService.extractFile(target, analysisProperties.getMaxPromptSize());
        if (!extraction.isSuccess()) {
            return Flux.just("无法提取文件内容: " + extraction.getErrorMessage());
        }

        String content = extraction.getContent();
        if (content.length() > analysisProperties.getMaxPromptSize()) {
            content = content.substring(0, analysisProperties.getMaxPromptSize()) + "\n... [内容已截断]";
        }

        String prompt = String.format(
            "你是一个数据分析专家。请分析以下实验数据文件 \"%s\" 的内容，给出：\n" +
            "1. **数据概览**：文件包含了什么数据，有哪些关键字段\n" +
            "2. **关键发现**：从数据中发现的重要指标和规律\n" +
            "3. **问题诊断**：可能存在的问题（如成功率低、延迟高等）\n" +
            "4. **优化建议**：基于数据给出参数调优或策略改进建议\n\n" +
            "文件内容如下：\n```\n%s\n```",
            file.getName(), content
        );

        // 获取文件分类
        String category = getFileCategory(name);

        // 流式返回，完成后异步保存结果
        StringBuilder resultContent = new StringBuilder();
        return chatService.streamChat(provider, prompt, sessionId)
            .doOnNext(resultContent::append)
            .doOnComplete(() -> {
                try {
                    AnalysisResult analysisResult = new AnalysisResult(
                        UUID.randomUUID().toString(),
                        filePath,
                        file.getName(),
                        provider,
                        sessionId,
                        LocalDateTime.now(),
                        resultContent.toString(),
                        file.length(),
                        category
                    );
                    analysisResultService.saveResult(analysisResult);
                } catch (Exception ignored) {}
            });
    }

    private String getFileCategory(String name) {
        if (name.endsWith(".json")) return "json";
        if (name.endsWith(".csv")) return "csv";
        if (name.endsWith(".pdf")) return "pdf";
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) return "excel";
        if (name.endsWith(".docx")) return "word";
        if (name.endsWith(".md")) return "markdown";
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".tex")) return "latex";
        if (name.endsWith(".png") || name.endsWith(".jpg")) return "image";
        return "other";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
