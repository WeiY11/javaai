package com.example.evimind.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class LoginRequest {

  @NotBlank
  @Size(min = 3, max = 64)
  private String username;

  @NotBlank
  @Size(min = 6, max = 128)
  private String password;
}
