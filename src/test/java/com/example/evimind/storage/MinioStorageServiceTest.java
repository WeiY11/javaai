package com.example.evimind.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.evimind.config.MinioConfig;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;

class MinioStorageServiceTest {

  @Test
  void checkHealthReportsUpOnlyAfterBucketConnectivitySucceeds() throws Exception {
    MinioStorageService service = serviceWithBucket("evimind-documents", true);

    Map<String, Object> status = service.checkHealth();

    assertThat(status)
        .containsEntry("status", "UP")
        .containsEntry("bucket", "evimind-documents");
  }

  @Test
  void checkHealthReportsDownWhenConfiguredBucketIsMissing() throws Exception {
    MinioStorageService service = serviceWithBucket("missing-bucket", false);

    Map<String, Object> status = service.checkHealth();

    assertThat(status)
        .containsEntry("status", "DOWN")
        .containsEntry("bucket", "missing-bucket")
        .containsEntry("message", "MinIO bucket does not exist");
  }

  private static MinioStorageService serviceWithBucket(String bucket, boolean exists)
      throws Exception {
    MinioConfig config = new MinioConfig();
    config.setBucket(bucket);
    MinioClient minioClient = mock(MinioClient.class);
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(exists);

    MinioStorageService service = new MinioStorageService(config);
    ReflectionTestUtils.setField(service, "minioClient", minioClient);
    return service;
  }
}
