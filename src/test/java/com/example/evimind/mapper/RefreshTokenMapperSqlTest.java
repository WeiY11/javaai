package com.example.evimind.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class RefreshTokenMapperSqlTest {

  @Test
  void consumeActiveTokenSqlShouldBeSingleUseAndRejectExpiredTokens() throws Exception {
    Method method =
        RefreshTokenMapper.class.getMethod(
            "consumeActiveToken", Long.class, LocalDateTime.class);
    Update annotation = method.getAnnotation(Update.class);
    String sql =
        String.join("\n", annotation.value())
            .replace("#{id}", "?")
            .replace("#{now}", "?");

    try (Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:refresh-token-cas;DB_CLOSE_DELAY=-1")) {
      connection
          .createStatement()
          .execute(
              """
              CREATE TABLE refresh_token (
                id BIGINT PRIMARY KEY,
                expires_at TIMESTAMP NOT NULL,
                revoked BOOLEAN NOT NULL
              )
              """);

      LocalDateTime now = LocalDateTime.now();
      insertToken(connection, 1L, now.plusMinutes(5), false);
      insertToken(connection, 2L, now.minusMinutes(1), false);

      assertThat(consume(connection, sql, 1L, now)).isEqualTo(1);
      assertThat(consume(connection, sql, 1L, now)).isZero();
      assertThat(consume(connection, sql, 2L, now)).isZero();
    }
  }

  private void insertToken(
      Connection connection, long id, LocalDateTime expiresAt, boolean revoked) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO refresh_token (id, expires_at, revoked) VALUES (?, ?, ?)")) {
      statement.setLong(1, id);
      statement.setTimestamp(2, Timestamp.valueOf(expiresAt));
      statement.setBoolean(3, revoked);
      statement.executeUpdate();
    }
  }

  private int consume(
      Connection connection, String sql, long id, LocalDateTime now) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      statement.setTimestamp(2, Timestamp.valueOf(now));
      return statement.executeUpdate();
    }
  }
}
