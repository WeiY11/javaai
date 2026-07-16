package com.example.evimind.config;

import java.util.Arrays;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductionSecurityConfigValidator {

  private static final Set<String> KNOWN_DEVELOPMENT_SECRETS =
      Set.of(
          "dev-only-secret-key-for-local-testing-2026",
          "evimind-standalone-jwt-secret-change-in-production",
          "myDefaultJwtSecretKeyForDevOnlyPleaseReplaceInProduction2026");
  private static final Set<String> LOCAL_PROFILES = Set.of("dev", "standalone", "test");

  private final JwtConfig jwtConfig;
  private final Environment environment;

  @PostConstruct
  void validate() {
    if (isLocalProfile()) {
      return;
    }

    String secret = jwtConfig.getSecret();
    if (!StringUtils.hasText(secret) || KNOWN_DEVELOPMENT_SECRETS.contains(secret)) {
      throw new IllegalStateException(
          "JWT_SECRET must be a non-default secret outside dev, standalone, or test profiles");
    }
  }

  private boolean isLocalProfile() {
    String[] activeProfiles = environment.getActiveProfiles();
    return activeProfiles.length > 0 && Arrays.stream(activeProfiles).allMatch(LOCAL_PROFILES::contains);
  }
}
