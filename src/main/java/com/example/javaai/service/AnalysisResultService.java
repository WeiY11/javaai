package com.example.javaai.service;

import com.example.javaai.config.AnalysisProperties;
import com.example.javaai.model.AnalysisResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalysisResultService {

    private final AnalysisProperties properties;
    private final String baseDir;
    private final ObjectMapper objectMapper;
    private final Path resultRoot;

    public AnalysisResultService(AnalysisProperties properties,
                                 @Value("${custom.data.base-dir}") String baseDir) {
        this.properties = properties;
        this.baseDir = baseDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.resultRoot = Paths.get(baseDir, properties.getResultDir());
    }

    public void saveResult(AnalysisResult result) {
        try {
            Files.createDirectories(resultRoot);

            String hash = String.valueOf(Math.abs(result.getFilePath().hashCode()));
            Path dir = resultRoot.resolve(hash);
            Files.createDirectories(dir);

            // 写入结果文件
            Path file = dir.resolve(result.getId() + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), result);

            // 更新索引
            updateIndex(result.getFilePath(), result.getId());

        } catch (IOException e) {
            throw new RuntimeException("保存分析结果失败: " + e.getMessage(), e);
        }
    }

    public List<AnalysisResult> getResultsByFile(String filePath) {
        try {
            Map<String, List<String>> index = loadIndex();
            List<String> ids = index.getOrDefault(filePath, Collections.emptyList());
            List<AnalysisResult> results = new ArrayList<>();
            for (String id : ids) {
                AnalysisResult r = loadResultById(id, filePath);
                if (r != null) results.add(r);
            }
            results.sort((a, b) -> b.getAnalyzedAt().compareTo(a.getAnalyzedAt()));
            return results;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getAllResults(int page, int size) {
        try {
            List<AnalysisResult> all = new ArrayList<>();
            if (Files.exists(resultRoot)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(resultRoot)) {
                    for (Path dir : stream) {
                        if (Files.isDirectory(dir)) {
                            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
                                for (Path file : files) {
                                    if (!file.getFileName().toString().equals("index.json")) {
                                        try {
                                            all.add(objectMapper.readValue(file.toFile(), AnalysisResult.class));
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
            all.sort((a, b) -> b.getAnalyzedAt().compareTo(a.getAnalyzedAt()));

            int total = all.size();
            int fromIndex = Math.min(page * size, total);
            int toIndex = Math.min(fromIndex + size, total);
            List<AnalysisResult> pageData = all.subList(fromIndex, toIndex);

            return Map.of(
                "results", pageData,
                "total", total,
                "page", page,
                "size", size
            );
        } catch (IOException e) {
            return Map.of("results", Collections.emptyList(), "total", 0, "page", page, "size", size);
        }
    }

    public List<AnalysisResult> getResultsByIds(List<String> resultIds) {
        List<AnalysisResult> results = new ArrayList<>();
        try {
            if (Files.exists(resultRoot)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(resultRoot)) {
                    for (Path dir : stream) {
                        if (Files.isDirectory(dir)) {
                            for (String id : resultIds) {
                                Path file = dir.resolve(id + ".json");
                                if (Files.exists(file)) {
                                    try {
                                        results.add(objectMapper.readValue(file.toFile(), AnalysisResult.class));
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException ignored) {}
        return results;
    }

    private void updateIndex(String filePath, String resultId) throws IOException {
        Map<String, List<String>> index = loadIndex();
        index.computeIfAbsent(filePath, k -> new ArrayList<>()).add(resultId);
        Path indexFile = resultRoot.resolve("index.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), index);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> loadIndex() throws IOException {
        Path indexFile = resultRoot.resolve("index.json");
        if (Files.exists(indexFile)) {
            return objectMapper.readValue(indexFile.toFile(),
                new TypeReference<Map<String, List<String>>>() {});
        }
        return new HashMap<>();
    }

    private AnalysisResult loadResultById(String id, String filePath) {
        String hash = String.valueOf(Math.abs(filePath.hashCode()));
        Path file = resultRoot.resolve(hash).resolve(id + ".json");
        if (Files.exists(file)) {
            try {
                return objectMapper.readValue(file.toFile(), AnalysisResult.class);
            } catch (IOException e) {
                return null;
            }
        }
        // fallback: search all dirs
        try {
            if (Files.exists(resultRoot)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(resultRoot)) {
                    for (Path dir : stream) {
                        Path f = dir.resolve(id + ".json");
                        if (Files.exists(f)) {
                            return objectMapper.readValue(f.toFile(), AnalysisResult.class);
                        }
                    }
                }
            }
        } catch (IOException ignored) {}
        return null;
    }
}
