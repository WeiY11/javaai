package com.example.evimind.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.example.evimind.config.JwtConfig;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class TokenProviderTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(TokenProvider.class);
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
  void invalidTokenLogsOnlyTheExceptionType() {
    JwtConfig jwtConfig = new JwtConfig();
    jwtConfig.setSecret("jwt-test-secret-key-with-at-least-thirty-two-characters");
    TokenProvider tokenProvider = new TokenProvider(jwtConfig);
    String sensitiveToken = "malformed-token-contains-secret";

    assertThat(tokenProvider.validateToken(sensitiveToken)).isFalse();

    assertThat(logAppender.list).hasSize(1);
    assertThat(logAppender.list.getFirst().getFormattedMessage())
        .contains("Invalid JWT token (")
        .doesNotContain(sensitiveToken);
    assertThat(logAppender.list.getFirst().getThrowableProxy()).isNull();
  }
}
