package com.example.evimind.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.example.evimind.model.dto.LoginRequest;
import com.example.evimind.model.dto.RefreshTokenRequest;

class AuthRequestValidationTest {

  private final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void refreshRequestShouldRejectBlankTokens() {
    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken(" ");

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("refreshToken");
  }

  @Test
  void loginRequestShouldRejectBlankCredentials() {
    LoginRequest request = new LoginRequest();
    request.setUsername(" ");
    request.setPassword("");

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("username", "password");
  }

  @Test
  void controllerShouldTriggerValidationBeforeCallingAuthService() throws Exception {
    Method login = AuthController.class.getMethod("login", LoginRequest.class);
    Method refresh = AuthController.class.getMethod("refresh", RefreshTokenRequest.class);

    assertThat(login.getParameterAnnotations()[0])
        .anyMatch(annotation -> annotation.annotationType() == Valid.class);
    assertThat(refresh.getParameterAnnotations()[0])
        .anyMatch(annotation -> annotation.annotationType() == Valid.class);
  }
}
