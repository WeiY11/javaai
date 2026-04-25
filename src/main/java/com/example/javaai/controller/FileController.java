package com.example.javaai.controller;

import com.example.javaai.service.ChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${custom.data.base-dir}")
    private String baseDir;

    private final ChatService chatService;

    public FileController(ChatService chatService) {
        this.chatService = chatService;
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
                // 跳过隐藏文件和目录 (如 .git, .vscode 等)
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

                // 判断文件类型分类
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
                    else item.put("category", "other");
                }
                items.add(item);
            }
        }

        // 默认排序：目录优先，然后按名称
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

        if (name.endsWith(".png") || name.endsWith(".jpg")) {
            // 图片返回 base64
            byte[] bytes = Files.readAllBytes(target);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mime = name.endsWith(".png") ? "image/png" : "image/jpeg";
            result.put("type", "image");
            result.put("content", "data:" + mime + ";base64," + base64);
        } else if (name.endsWith(".pt") || name.endsWith(".pth") || name.endsWith(".pdf")) {
            // 二进制文件不读取内容
            result.put("type", "binary");
            result.put("content", "[二进制文件，大小: " + formatSize(file.length()) + "]");
        } else {
            // 文本文件直接读取 (JSON, CSV, MD, PY, TEX 等)
            String content = Files.readString(target);
            // 限制最大返回 500KB 文本
            if (content.length() > 512000) {
                content = content.substring(0, 512000) + "\n... [内容已截断，文件过大]";
            }
            if (name.endsWith(".json")) result.put("type", "json");
            else if (name.endsWith(".csv")) result.put("type", "csv");
            else if (name.endsWith(".md")) result.put("type", "markdown");
            else result.put("type", "text");

            result.put("content", content);
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
        if (name.endsWith(".pt") || name.endsWith(".pth") || name.endsWith(".pdf")) {
            return Flux.just("该文件为二进制文件，无法直接进行文本分析。");
        }

        String content = Files.readString(target);
        // 限制发送给 AI 的内容长度
        if (content.length() > 100000) {
            content = content.substring(0, 100000) + "\n... [内容已截断]";
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

        return chatService.streamChat(provider, prompt, sessionId);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
