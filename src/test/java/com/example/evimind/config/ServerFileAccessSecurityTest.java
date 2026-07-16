package com.example.evimind.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.example.evimind.auth.AuthController;
import com.example.evimind.auth.AuthService;
import com.example.evimind.auth.JwtAuthenticationFilter;
import com.example.evimind.auth.TokenProvider;
import com.example.evimind.common.HealthController;
import com.example.evimind.controller.FileController;
import com.example.evimind.identity.GroupService;
import com.example.evimind.service.AnalysisResultService;
import com.example.evimind.service.ChatService;
import com.example.evimind.service.FileExtractorService;

@WebMvcTest({FileController.class, HealthController.class, AuthController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ServerFileAccessSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TokenProvider tokenProvider;
  @MockBean private GroupService groupService;
  @MockBean private AuthService authService;
  @MockBean private ChatService chatService;
  @MockBean private FileExtractorService fileExtractorService;
  @MockBean private AnalysisResultService analysisResultService;
  @MockBean private AnalysisProperties analysisProperties;
  @MockBean private DataSource dataSource;

  @Test
  void anonymousUsersCannotReadDetailedRuntimeHealth() throws Exception {
    mockMvc.perform(get("/api/v1/health")).andExpect(status().isUnauthorized());
  }

  @Test
  void anonymousUsersCannotReadTheirCurrentUserProfile() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void anonymousUsersCanStillSubmitLoginCredentials() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"password\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "USER")
  void regularUsersCannotReadDetailedRuntimeHealth() throws Exception {
    mockMvc.perform(get("/api/v1/health")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void administratorsCanReadDetailedRuntimeHealth() throws Exception {
    mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
  }

  @Test
  void anonymousUsersCannotReachSwaggerOrPrometheus() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "USER")
  void regularUsersCannotReachSwaggerOrPrometheus() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isForbidden());
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "USER")
  void regularUsersCannotReachTheH2Console() throws Exception {
    mockMvc.perform(get("/h2-console/")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "USER")
  void regularUsersCannotBrowseTheServerDataDirectory() throws Exception {
    mockMvc.perform(get("/api/files")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "USER")
  void regularUsersCannotStartOrReadServerSideAnalysis() throws Exception {
    mockMvc.perform(get("/api/analysis/results")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void administratorsCanReachTheFileController() throws Exception {
    mockMvc.perform(get("/api/files")).andExpect(status().isOk());
  }
}
