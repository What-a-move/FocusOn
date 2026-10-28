package com.capstone.backend.goal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.common.api.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 목표 API의 인증과 사용자별 소유권을 검증한다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class GoalApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @MockitoBean private GoogleOauthClient google;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "goal-" + UUID.randomUUID());
  }

  @Test
  void manualGoalIsCreatedAndOnlyOwnerCanRead() throws Exception {
    LoginResponse owner = desktop("goal-owner");
    LoginResponse other = desktop("goal-other");
    String body = "{\"goalText\":\"Spring 학습\","
        + "\"confirmationSource\":\"MANUAL_CONFIRMED\"}";
    String response = mvc.perform(post("/api/v1/goals")
            .header("Authorization", "Bearer " + owner.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.data.goalText").value("Spring 학습"))
        .andReturn().getResponse().getContentAsString();
    JsonNode data = json.readTree(response).path("data");
    String id = data.path("goalId").asText();
    mvc.perform(get("/api/v1/goals/" + id)
            .header("Authorization", "Bearer " + owner.accessToken()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.goalId").value(id));
    mvc.perform(get("/api/v1/goals/" + id)
            .header("Authorization", "Bearer " + other.accessToken()))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
    mvc.perform(get("/api/v1/goals/not-a-number")
            .header("Authorization", "Bearer " + owner.accessToken()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/goals/999999999999999999999999")
            .header("Authorization", "Bearer " + owner.accessToken()))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
    mvc.perform(get("/api/v1/goals/" + id)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void goalLengthAndUnverifiedAiConfirmationAreRejected() throws Exception {
    LoginResponse owner = desktop("goal-validation");
    for (String text : new String[] {"a", "a".repeat(41)}) {
      mvc.perform(post("/api/v1/goals").header("Authorization", "Bearer " + owner.accessToken())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"goalText\":\"" + text
                  + "\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("GOAL_TEXT_INVALID"));
    }
    mvc.perform(post("/api/v1/goals").header("Authorization", "Bearer " + owner.accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"  \" ,\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("GOAL_TEXT_INVALID"));
    mvc.perform(post("/api/v1/goals").header("Authorization", "Bearer " + owner.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content("{\"goalText\":\"목표\"}"))
        .andExpect(status().isBadRequest());
    for (String text : new String[] {"ab", "a".repeat(40)}) {
      mvc.perform(post("/api/v1/goals").header("Authorization", "Bearer " + owner.accessToken())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"goalText\":\"" + text
                  + "\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
          .andExpect(status().isCreated());
    }
    mvc.perform(post("/api/v1/goals").header("Authorization", "Bearer " + owner.accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"AI 목표\",\"confirmationSource\":\"AI_CONFIRMED\","
                + "\"clarificationSessionId\":\"123\"}"))
        .andExpect(status().isServiceUnavailable());
    String manualResponse = mvc.perform(post("/api/v1/goals")
            .header("Authorization", "Bearer " + owner.accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"수동 목표\",\"confirmationSource\":\"MANUAL_CONFIRMED\","
                + "\"clarificationSessionId\":\"123\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.confirmationSource").value("MANUAL_CONFIRMED"))
        .andReturn().getResponse().getContentAsString();
    String manualId = json.readTree(manualResponse).path("data").path("goalId").asText();
    mvc.perform(get("/api/v1/goals/" + manualId)
            .header("Authorization", "Bearer " + owner.accessToken()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.goalText").value("수동 목표"));
  }

  private LoginResponse desktop(String installationId) {
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), installationId,
        "http://127.0.0.1:49152", new DesktopDevice(installationId, "QA", "MACOS")));
  }
}
