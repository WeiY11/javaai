package com.example.evimind.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import com.example.evimind.auth.JwtAuthenticationFilter;

class SecurityConfigTest {

  @Test
  void corsDefaultsAllowOnlyLocalFrontendOrigins() {
    SecurityConfig config = new SecurityConfig(mock(JwtAuthenticationFilter.class));

    CorsConfiguration cors =
        config
            .corsConfigurationSource()
            .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/health"));

    assertThat(cors).isNotNull();
    assertThat(cors.getAllowedOriginPatterns())
        .containsExactly("http://localhost:5173", "http://127.0.0.1:5173")
        .doesNotContain("*");
    assertThat(cors.getAllowCredentials()).isTrue();
  }
}
