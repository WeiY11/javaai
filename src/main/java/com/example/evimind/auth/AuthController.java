package com.example.evimind.auth;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.evimind.model.dto.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.ok(ApiResponse.success(authService.register(request)));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthResponse>> refresh(
      @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(ApiResponse.success(authService.refreshToken(request)));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserInfo>> me() {
    return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUser()));
  }
}
