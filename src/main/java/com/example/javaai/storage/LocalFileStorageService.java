package com.example.javaai.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "false", matchIfMissing = false)
public class LocalFileStorageService {

    @Value("${custom.data.base-dir:/data}")
    private String baseDir;

    public void uploadFile(String objectName, InputStream inputStream, long size, String contentType) throws IOException {
        Path targetPath = Paths.get(baseDir, "documents", objectName);
        Files.createDirectories(targetPath.getParent());
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored file locally: {}", targetPath);
    }

    public InputStream downloadFile(String objectName) throws IOException {
        Path filePath = Paths.get(baseDir, "documents", objectName);
        return Files.newInputStream(filePath);
    }

    public boolean fileExists(String objectName) {
        return Files.exists(Paths.get(baseDir, "documents", objectName));
    }

    public void deleteFile(String objectName) throws IOException {
        Path filePath = Paths.get(baseDir, "documents", objectName);
        Files.deleteIfExists(filePath);
        Path parent = filePath.getParent();
        if (Files.isDirectory(parent) && Files.list(parent).findAny().isEmpty()) {
            Files.deleteIfExists(parent);
        }
    }
}
