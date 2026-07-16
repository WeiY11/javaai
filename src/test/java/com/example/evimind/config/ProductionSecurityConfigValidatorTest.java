package com.example.evimind.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityConfigValidatorTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(JwtConfig.class, ProductionSecurityConfigValidator.class);

  @Test
  void productionLikeProfilesRejectKnownDevelopmentSecrets() {
    assertThatThrownBy(
            () ->
                validator("myDefaultJwtSecretKeyForDevOnlyPleaseReplaceInProduction2026")
                    .validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void localProfilesMayUseTheirBundledDevelopmentSecrets() {
    assertThatCode(
            () ->
                validator("dev-only-secret-key-for-local-testing-2026", "dev").validate())
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                validator("evimind-standalone-jwt-secret-change-in-production", "standalone")
                    .validate())
        .doesNotThrowAnyException();
  }

  @Test
  void mixedLocalAndProductionProfilesRejectDevelopmentSecrets() {
    assertThatThrownBy(
            () ->
                validator("dev-only-secret-key-for-local-testing-2026", "dev", "production")
                    .validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void productionLikeProfilesAcceptAnUnrecognisedSecret() {
    assertThatCode(
            () ->
                validator("a-different-secure-secret-with-at-least-thirty-two-characters").validate())
        .doesNotThrowAnyException();
  }

  @Test
  void springContextFailsBeforeStartupWithAKnownDevelopmentSecret() {
    contextRunner
        .withPropertyValues(
            "jwt.secret=myDefaultJwtSecretKeyForDevOnlyPleaseReplaceInProduction2026")
        .run(context -> context.assertThat().hasFailed());
  }

  @Test
  void springContextRejectsMissingOrShortJwtSecrets() {
    contextRunner.run(context -> context.assertThat().hasFailed());
    contextRunner
        .withPropertyValues("jwt.secret=too-short")
        .run(context -> context.assertThat().hasFailed());
  }

  @Test
  void springContextAcceptsAValidProductionSecret() {
    contextRunner
        .withPropertyValues("jwt.secret=a-different-secure-secret-with-at-least-thirty-two-characters")
        .run(context -> context.assertThat().hasNotFailed());
  }

  private static ProductionSecurityConfigValidator validator(String secret, String... activeProfiles) {
    JwtConfig jwtConfig = new JwtConfig();
    jwtConfig.setSecret(secret);
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(activeProfiles);
    return new ProductionSecurityConfigValidator(jwtConfig, environment);
  }
}
