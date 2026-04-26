package com.example.javaai.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaai.config.JwtConfig;
import com.example.javaai.identity.GroupContext;
import com.example.javaai.mapper.RefreshTokenMapper;
import com.example.javaai.mapper.UserMapper;
import com.example.javaai.model.dto.*;
import com.example.javaai.model.entity.RefreshToken;
import com.example.javaai.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final TokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setSystemRole("USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        return generateAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new IllegalArgumentException("User account is disabled");
        }

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());
        RefreshToken rt = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getTokenHash, tokenHash)
                        .eq(RefreshToken::getRevoked, false)
        );
        if (rt == null || rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        rt.setRevoked(true);
        refreshTokenMapper.updateById(rt);

        User user = userMapper.selectById(rt.getUserId());
        if (user == null || "DISABLED".equals(user.getStatus())) {
            throw new IllegalArgumentException("User not found or disabled");
        }

        return generateAuthResponse(user);
    }

    public UserInfo getCurrentUser() {
        Long userId = GroupContext.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("Not authenticated");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return toUserInfo(user);
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getSystemRole());
        String refreshToken = tokenProvider.generateRefreshToken();

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(hashToken(refreshToken));
        rt.setExpiresAt(LocalDateTime.now().plusSeconds(jwtConfig.getRefreshTokenExpiration() / 1000));
        rt.setRevoked(false);
        refreshTokenMapper.insert(rt);

        return new AuthResponse(accessToken, refreshToken, toUserInfo(user));
    }

    private UserInfo toUserInfo(User user) {
        UserInfo info = new UserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setEmail(user.getEmail());
        info.setSystemRole(user.getSystemRole());
        return info;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Token hashing failed", e);
        }
    }
}
