package com.example.evimind.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

  @NotBlank(message = "JWT_SECRET must be configured")
  @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
  private String secret;
  private long accessTokenExpiration;
  private long refreshTokenExpiration;
}
