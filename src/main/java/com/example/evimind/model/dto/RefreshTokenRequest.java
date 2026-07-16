package com.example.evimind.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class RefreshTokenRequest {

  @NotBlank
  @Size(max = 2048)
  private String refreshToken;
}
