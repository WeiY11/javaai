package com.example.evimind.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class JwtConfigValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void secretMustBePresentAndLongEnoughForHmacSha256() {
    JwtConfig missingSecret = new JwtConfig();
    JwtConfig shortSecret = new JwtConfig();
    shortSecret.setSecret("too-short");

    assertThat(validator.validate(missingSecret)).isNotEmpty();
    assertThat(validator.validate(shortSecret)).isNotEmpty();
  }

  @Test
  void acceptsASecretThatMeetsTheMinimumLength() {
    JwtConfig config = new JwtConfig();
    config.setSecret("secure-test-secret-with-at-least-thirty-two-characters");

    assertThat(validator.validate(config)).isEmpty();
  }
}
