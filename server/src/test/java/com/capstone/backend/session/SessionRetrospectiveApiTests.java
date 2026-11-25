package com.capstone.backend.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.persistence.UserEntity.OnboardingStatus;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.user.service.OnboardingReadinessService;
import com.capstone.backend.user.service.OnboardingReadinessService.Readiness;
import com.capstone.backend.user.service.OnboardingReadinessService.Steps;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** 세션 끝 회고 저장 규칙을 검증한다(#318). */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class SessionRetrospectiveApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LearningSessionRepository sessions;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private OnboardingReadinessService onboarding;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "retro-" + UUID.randomUUID());
    when(onboarding.get(any(), isNull())).thenReturn(new Readiness(OnboardingStatus.COMPLETED,
        "COMPLETED", new Steps(true, true, true, true, true)));
  }

  @Test
  void endedSessionSavesAndOverwritesRetrospective() throws Exception {
    var owner = desktop();
    String id = start(owner);
    put(owner, id, "{\"rating\":\"GOOD\"}").andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    end(owner, id);
    put(owner, id, "{\"rating\":\"BAD\",\"reasonCodes\":[\"APP_FALSE_ALERT\",\"SELF_TIRED\"],"
        + "\"note\":\"  알림이 너무 자주 떴어요  \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").value(id))
        .andExpect(jsonPath("$.data.rating").value("BAD"))
        .andExpect(jsonPath("$.data.reasonCodes[0]").value("APP_FALSE_ALERT"))
        .andExpect(jsonPath("$.data.note").value("알림이 너무 자주 떴어요"))
        .andExpect(jsonPath("$.data.updatedAt").isString());
    // 다시 보내면 덮어쓰고, BAD가 아니면 사유·메모는 비운다.
    put(owner, id, "{\"rating\":\"GOOD\",\"reasonCodes\":[]}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rating").value("GOOD"))
        .andExpect(jsonPath("$.data.reasonCodes.length()").value(0));
    assertEquals(1, jdbc.queryForObject(
        "SELECT count(*) FROM session_retrospectives WHERE session_id=?", Integer.class,
        Long.valueOf(id)));
    assertNull(jdbc.queryForObject("SELECT note FROM session_retrospectives WHERE session_id=?",
        String.class, Long.valueOf(id)));
  }

  @Test
  void invalidInputsAreRejected() throws Exception {
    var owner = desktop();
    String id = start(owner);
    end(owner, id);
    for (String bad : new String[] {
        "{}",
        "{\"rating\":\"MEH\"}",
        "{\"rating\":\"BAD\",\"reasonCodes\":[\"UNKNOWN\"]}",
        "{\"rating\":\"BAD\",\"reasonCodes\":[\"SELF_TIRED\",\"SELF_TIRED\"]}",
        "{\"rating\":\"GOOD\",\"reasonCodes\":[\"SELF_TIRED\"]}",
        "{\"rating\":\"OKAY\",\"note\":\"메모\"}",
        "{\"rating\":\"BAD\",\"note\":\"" + "가".repeat(101) + "\"}"}) {
      put(owner, id, bad).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    String sensitive = json.writeValueAsString(java.util.Map.of("rating", "BAD",
        "note", "연락은 qa@example.com 으로"));
    String body = put(owner, id, sensitive).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details.fieldErrors.note").value("SENSITIVE"))
        .andReturn().getResponse().getContentAsString();
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("qa@example.com"));
    put(owner, id, "{\"rating\":\"BAD\",\"note\":\"" + "가".repeat(100) + "\"}")
        .andExpect(status().isOk());
  }

  @Test
  void ownershipAndHeadersAreRequired() throws Exception {
    var owner = desktop();
    String id = start(owner);
    end(owner, id);
    var other = desktop();
    put(other, id, "{\"rating\":\"GOOD\"}").andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    put(owner, "not-a-number", "{\"rating\":\"GOOD\"}").andExpect(status().isNotFound());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
            "/api/v1/sessions/" + id + "/retrospective")
            .header("Authorization", token(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":\"GOOD\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
            "/api/v1/sessions/" + id + "/retrospective")
            .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions put(LoginResponse owner, String id, String body) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .put("/api/v1/sessions/" + id + "/retrospective").header("Authorization", token(owner))
        .header("X-Device-Id", owner.device().deviceId())
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String start(LoginResponse owner) throws Exception {
    String goal = mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"회고 목표\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String goalId = json.readTree(goal).path("data").path("goalId").asString();
    String started = mvc.perform(post("/api/v1/sessions").header("Authorization", token(owner))
            .header("X-Device-Id", owner.device().deviceId())
            .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalId\":\"" + goalId + "\",\"targetDurationMs\":300000,"
                + "\"timeZone\":\"Asia/Seoul\",\"sessionOptions\":{\"interventionLevel\":"
                + "\"GENTLE\",\"notificationChannels\":[\"MOLE_UI\"],"
                + "\"cameraAnalysisEnabled\":false},\"startReadiness\":"
                + "{\"screenAnalysisReady\":true}}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(started).path("data").path("sessionId").asString();
  }

  private void end(LoginResponse owner, String id) throws Exception {
    long version = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
    mvc.perform(post("/api/v1/sessions/" + id + "/end").header("Authorization", token(owner))
            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", version)
            .contentType(MediaType.APPLICATION_JSON).content("{\"accumulatedActiveMs\":0}"))
        .andExpect(status().isOk());
  }

  private String token(LoginResponse owner) {
    return "Bearer " + owner.accessToken();
  }

  private LoginResponse desktop() {
    String id = "retro-" + UUID.randomUUID();
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), id,
        "http://127.0.0.1:49152", new DesktopDevice(id, "QA", "MACOS")));
  }
}
