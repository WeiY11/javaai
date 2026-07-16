package com.example.evimind.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.evimind.config.JwtConfig;
import com.example.evimind.mapper.RefreshTokenMapper;
import com.example.evimind.mapper.UserMapper;
import com.example.evimind.model.dto.AuthResponse;
import com.example.evimind.model.dto.RefreshTokenRequest;
import com.example.evimind.model.entity.RefreshToken;
import com.example.evimind.model.entity.User;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserMapper userMapper;
  @Mock private RefreshTokenMapper refreshTokenMapper;
  @Mock private TokenProvider tokenProvider;
  @Mock private PasswordEncoder passwordEncoder;

  private JwtConfig jwtConfig;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    jwtConfig = new JwtConfig();
    jwtConfig.setRefreshTokenExpiration(86_400_000);
    authService =
        new AuthService(
            userMapper, refreshTokenMapper, tokenProvider, passwordEncoder, jwtConfig);
  }

  @Test
  void refreshShouldRejectTokenWhenAtomicConsumptionLosesTheRace() {
    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken("refresh-token");

    RefreshToken storedToken = new RefreshToken();
    storedToken.setId(7L);
    storedToken.setUserId(3L);
    storedToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));
    storedToken.setRevoked(false);

    when(refreshTokenMapper.selectOne(any(Wrapper.class))).thenReturn(storedToken);
    when(refreshTokenMapper.consumeActiveToken(eq(7L), any(LocalDateTime.class))).thenReturn(0);

    assertThatThrownBy(() -> authService.refreshToken(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid or expired refresh token");

    verify(tokenProvider, never()).generateRefreshToken();
    verify(refreshTokenMapper, never()).insert(any(RefreshToken.class));
  }

  @Test
  void refreshShouldRotateTokenAfterAtomicConsumptionSucceeds() {
    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken("refresh-token");

    RefreshToken storedToken = new RefreshToken();
    storedToken.setId(7L);
    storedToken.setUserId(3L);
    storedToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));
    storedToken.setRevoked(false);

    User user = new User();
    user.setId(3L);
    user.setUsername("researcher");
    user.setEmail("researcher@example.test");
    user.setSystemRole("USER");
    user.setStatus("ACTIVE");

    when(refreshTokenMapper.selectOne(any(Wrapper.class))).thenReturn(storedToken);
    when(refreshTokenMapper.consumeActiveToken(eq(7L), any(LocalDateTime.class))).thenReturn(1);
    when(userMapper.selectById(3L)).thenReturn(user);
    when(tokenProvider.generateAccessToken(3L, "researcher", "USER"))
        .thenReturn("new-access-token");
    when(tokenProvider.generateRefreshToken()).thenReturn("new-refresh-token");

    AuthResponse response = authService.refreshToken(request);

    assertThat(response.getAccessToken()).isEqualTo("new-access-token");
    assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
    assertThat(response.getUserInfo().getId()).isEqualTo(3L);
    verify(refreshTokenMapper).insert(any(RefreshToken.class));
    verify(refreshTokenMapper, never()).updateById(any(RefreshToken.class));
  }
}
