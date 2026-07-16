package com.example.evimind.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class DeepSeekHttpRequestInterceptorTest {

  private final DeepSeekHttpRequestInterceptor interceptor = new DeepSeekHttpRequestInterceptor();
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(DeepSeekHttpRequestInterceptor.class);
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
  void malformedPayloadDoesNotExposePromptContentInLogs() throws Exception {
    String sensitivePrompt = "confidential-research-prompt";
    byte[] payload = ("not-json-" + sensitivePrompt).getBytes(StandardCharsets.UTF_8);
    HttpRequest request = mock(HttpRequest.class);
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(request.getURI()).thenReturn(URI.create("https://api.deepseek.com/chat/completions"));
    when(execution.execute(same(request), any(byte[].class))).thenReturn(response);

    interceptor.intercept(request, payload, execution);

    verify(execution).execute(request, payload);
    assertThat(logAppender.list).hasSize(1);
    ILoggingEvent event = logAppender.list.getFirst();
    assertThat(event.getFormattedMessage()).contains("JsonParseException").doesNotContain(sensitivePrompt);
    assertThat(event.getThrowableProxy()).isNull();
  }
}
