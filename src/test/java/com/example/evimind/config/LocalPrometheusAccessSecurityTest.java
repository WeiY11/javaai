package com.example.evimind.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.example.evimind.auth.JwtAuthenticationFilter;
import com.example.evimind.auth.TokenProvider;
import com.example.evimind.common.HealthController;
import com.example.evimind.identity.GroupService;

@WebMvcTest(
    value = HealthController.class,
    properties = "custom.management.prometheus.require-admin=false")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class LocalPrometheusAccessSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TokenProvider tokenProvider;
  @MockBean private GroupService groupService;
  @MockBean private DataSource dataSource;

  @Test
  void localMonitoringConfigurationLetsThePrometheusRequestReachTheEndpoint() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
  }
}
