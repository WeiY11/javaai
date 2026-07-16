package com.example.evimind.storage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.evimind.config.MinioConfig;

import io.minio.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class MinioStorageService {

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private MinioClient minioClient;

  private final MinioConfig minioConfig;

  public MinioStorageService(MinioConfig minioConfig) {
    this.minioConfig = minioConfig;
  }

  @PostConstruct
  public void init() {
    if (minioClient == null || minioConfig == null) {
      log.warn("MinIO not configured, object storage disabled");
      return;
    }
    try {
      ensureBucketExists();
    } catch (Exception e) {
      log.warn("MinIO not available, running without object storage: {}", e.getMessage());
    }
  }

  private void ensureBucketExists() {
    try {
      boolean exists =
          minioClient.bucketExists(
              BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
      if (!exists) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
        log.info("Created MinIO bucket: {}", minioConfig.getBucket());
      }
    } catch (Exception e) {
      log.error("Failed to check/create MinIO bucket", e);
      throw new RuntimeException("MinIO bucket initialization failed", e);
    }
  }

  public Map<String, Object> checkHealth() {
    Map<String, Object> status = new LinkedHashMap<>();
    if (minioClient == null || minioConfig == null) {
      status.put("status", "NOT_CONFIGURED");
      status.put("message", "MinIO storage service is not configured");
      status.put(
          "action", "Set MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, and MINIO_BUCKET");
      return status;
    }
    try {
      boolean bucketExists =
          minioClient.bucketExists(
              BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
      status.put("status", bucketExists ? "UP" : "DOWN");
      status.put("bucket", minioConfig.getBucket());
      if (!bucketExists) {
        status.put("message", "MinIO bucket does not exist");
        status.put("action", "Create the configured MINIO_BUCKET or check MinIO bucket permissions");
      }
    } catch (Exception e) {
      status.put("status", "DOWN");
      status.put("error", e.getMessage());
      status.put("message", "MinIO health check failed");
      status.put("action", "Check MinIO service, bucket permissions, and MINIO_ENDPOINT");
    }
    return status;
  }

  public String uploadFile(
      String objectName, InputStream inputStream, long size, String contentType) {
    try {
      minioClient.putObject(
          PutObjectArgs.builder().bucket(minioConfig.getBucket()).object(objectName).stream(
                  inputStream, size, -1)
              .contentType(contentType)
              .build());
      log.info("Uploaded file to MinIO: {}", objectName);
      return objectName;
    } catch (Exception e) {
      log.error("Failed to upload file to MinIO: {}", objectName, e);
      throw new RuntimeException("File upload failed", e);
    }
  }

  public InputStream downloadFile(String objectName) {
    try {
      return minioClient.getObject(
          GetObjectArgs.builder().bucket(minioConfig.getBucket()).object(objectName).build());
    } catch (Exception e) {
      log.error("Failed to download file from MinIO: {}", objectName, e);
      throw new RuntimeException("File download failed", e);
    }
  }

  public void deleteFile(String objectName) {
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(minioConfig.getBucket()).object(objectName).build());
      log.info("Deleted file from MinIO: {}", objectName);
    } catch (Exception e) {
      log.error("Failed to delete file from MinIO: {}", objectName, e);
      throw new RuntimeException("File deletion failed", e);
    }
  }

  public List<String> listFiles(String prefix) {
    try {
      Iterable<Result<Item>> results =
          minioClient.listObjects(
              ListObjectsArgs.builder().bucket(minioConfig.getBucket()).prefix(prefix).build());
      List<String> files = new ArrayList<>();
      for (Result<Item> result : results) {
        files.add(result.get().objectName());
      }
      return files;
    } catch (Exception e) {
      log.error("Failed to list files from MinIO with prefix: {}", prefix, e);
      throw new RuntimeException("File listing failed", e);
    }
  }

  public String getPresignedUrl(String objectName, int expirySeconds) {
    try {
      return minioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .bucket(minioConfig.getBucket())
              .object(objectName)
              .expiry(expirySeconds)
              .build());
    } catch (Exception e) {
      log.error("Failed to generate presigned URL for: {}", objectName, e);
      throw new RuntimeException("Presigned URL generation failed", e);
    }
  }
}
