package com.example.evimind.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
  private String accessToken;
  private String refreshToken;
  private UserInfo userInfo;
}
