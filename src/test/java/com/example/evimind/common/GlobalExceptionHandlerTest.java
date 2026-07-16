package com.example.evimind.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.example.evimind.model.dto.ApiResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void captureLogs() {
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void stopCapturingLogs() {
    logger.detachAppender(logAppender);
    logAppender.stop();
  }

  @Test
  void runtimeExceptionShouldNotExposeInternalMessage() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleRuntime(new RuntimeException("jdbc password=secret-token"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("Internal server error");
  }

  @Test
  void runtimeExceptionShouldNotWriteSensitiveMessageToLogs() {
    String directSecret = "jdbc password=secret-token";
    String causeSecret = "api token=nested-secret-token";

    handler.handleRuntime(
        new RuntimeException(directSecret, new IllegalStateException(causeSecret)));

    assertThat(logAppender.list).hasSize(1);
    ILoggingEvent event = logAppender.list.getFirst();
    String throwableText =
        event.getThrowableProxy() == null
            ? ""
            : ThrowableProxyUtil.asString(event.getThrowableProxy());
    assertThat(event.getFormattedMessage() + throwableText)
        .doesNotContain(directSecret, causeSecret);
  }

  @Test
  void oversizedUploadShouldUsePayloadTooLargeStatus() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024));

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("Uploaded file is too large");
  }
}
