package com.capstone.backend.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.persistence.UserEntity.OnboardingStatus;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.common.api.RequestIdFilter;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.user.service.OnboardingReadinessService;
import com.capstone.backend.user.service.OnboardingReadinessService.Readiness;
import com.capstone.backend.user.service.OnboardingReadinessService.Steps;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** HTTP 요청을 통해 시작, 재시도 및 사용자 분리를 확인한다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class SessionEndTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private LearningSessionRepository sessions;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired private com.capstone.backend.session.service.SessionCommandService commands;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private OnboardingReadinessService onboarding;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "session-" + UUID.randomUUID());
    when(onboarding.get(any(), isNull())).thenReturn(new Readiness(OnboardingStatus.COMPLETED,
        "COMPLETED", new Steps(true, true, true, true, true)));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "RUNNING", "PAUSED", "AUTO_PAUSED", "AWAITING_END_CONFIRMATION"
  })
  void endPreservesConfirmedTimeAndReleasesActiveSlot(String state) throws Exception {
    var fixture = started();
    jdbc.update("UPDATE learning_sessions SET status = ?, accumulated_active_ms = 1234"
        + " WHERE id = ?",
        state, Long.valueOf(fixture.id()));
    String key = UUID.randomUUID().toString();
    String first = end(fixture, key, fixture.version()).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ENDED"))
        .andExpect(jsonPath("$.data.accumulatedActiveMs").value(1234))
        .andExpect(jsonPath("$.data.targetDurationMs").value(1800000))
        .andExpect(jsonPath("$.data.version").value(fixture.version() + 1))
        .andExpect(jsonPath("$.data.endedAt").isString())
        .andReturn().getResponse().getContentAsString();
    String replay = end(fixture, key, fixture.version()).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    org.junit.jupiter.api.Assertions.assertEquals(json.readTree(first), json.readTree(replay));
    var stored = sessions.findById(Long.valueOf(fixture.id())).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("USER_ACTION", stored.getEndReason());
    org.junit.jupiter.api.Assertions.assertNull(stored.getLastResumedAt());
    org.junit.jupiter.api.Assertions.assertNotNull(stored.getEndedAt());
    mvc.perform(get("/api/v1/sessions/active").header("Authorization", fixture.token()))
        .andExpect(status().isNoContent());
    end(fixture, key, fixture.version() + 1).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    end(fixture, UUID.randomUUID().toString(), fixture.version()).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_VERSION_CONFLICT"));
    end(fixture, UUID.randomUUID().toString(), fixture.version() + 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"ENDED", "ENDING"})
  void terminalAndInProgressEndStatesCannotBeChanged(String state) throws Exception {
    var fixture = started();
    jdbc.update("UPDATE learning_sessions SET status = ? WHERE id = ?",
        state, Long.valueOf(fixture.id()));
    end(fixture, UUID.randomUUID().toString(), fixture.version())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    org.junit.jupiter.api.Assertions.assertEquals(state,
        sessions.findById(Long.valueOf(fixture.id())).orElseThrow().getStatus().name());
  }

  /** 서버가 이미 무신호로 끝낸 세션은 heartbeat와 같은 코드로 알리고 되살리지 않는다. */
  @Test
  void endingAbandonedSessionReturnsAbandonedAndKeepsServerState() throws Exception {
    var fixture = started();
    jdbc.update("UPDATE learning_sessions SET status='ABANDONED',end_reason='HEARTBEAT_TIMEOUT',"
        + "ended_at=now(),accumulated_active_ms=4321,last_resumed_at=NULL WHERE id=?",
        Long.valueOf(fixture.id()));
    long version = sessions.findById(Long.valueOf(fixture.id())).orElseThrow().getVersion();
    // 오래된 version으로 누른 종료도 같은 안내를 받는다.
    for (long sent : new long[] {version, fixture.version() + 7}) {
      end(fixture, UUID.randomUUID().toString(), sent).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("SESSION_ABANDONED"))
          .andExpect(jsonPath("$.retryable").value(false))
          .andExpect(jsonPath("$.details.currentStatus").value("ABANDONED"))
          .andExpect(jsonPath("$.details.endReason").value("HEARTBEAT_TIMEOUT"));
    }
    var stored = sessions.findById(Long.valueOf(fixture.id())).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("ABANDONED", stored.getStatus().name());
    org.junit.jupiter.api.Assertions.assertEquals("HEARTBEAT_TIMEOUT", stored.getEndReason());
    org.junit.jupiter.api.Assertions.assertEquals(4321, stored.getAccumulatedActiveMs());
    org.junit.jupiter.api.Assertions.assertEquals(version, stored.getVersion());
  }

  /** heartbeat가 저장한 누적값 이상으로만 종료할 수 있고 정상 값은 그 위로 확정된다. */
  @Test
  void endAfterHeartbeatKeepsSavedProgressAndAcceptsLaterFinalValue() throws Exception {
    var fixture = started();
    java.time.Instant resumed = java.time.Instant.now().minusSeconds(60);
    jdbc.update("UPDATE learning_sessions SET started_at=?,last_resumed_at=? WHERE id=?",
        java.sql.Timestamp.from(resumed), java.sql.Timestamp.from(resumed),
        Long.valueOf(fixture.id()));
    mvc.perform(post("/api/v1/sessions/" + fixture.id() + "/heartbeats")
            .header("Authorization", fixture.token()).header("X-Device-Id", fixture.deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clientObservedAt\":\"" + java.time.Instant.now()
                + "\",\"accumulatedActiveMs\":30000}"))
        .andExpect(status().isOk());
    var saved = sessions.findById(Long.valueOf(fixture.id())).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(30000, saved.getAccumulatedActiveMs());
    long version = saved.getVersion();
    // heartbeat가 이미 저장한 값보다 작은 최종값은 상태를 바꾸지 않고 거절한다.
    mvc.perform(post("/api/v1/sessions/" + fixture.id() + "/end")
            .header("Authorization", fixture.token())
            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", version)
            .contentType(MediaType.APPLICATION_JSON).content("{\"accumulatedActiveMs\":29999}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    org.junit.jupiter.api.Assertions.assertEquals("RUNNING",
        sessions.findById(Long.valueOf(fixture.id())).orElseThrow().getStatus().name());
    mvc.perform(post("/api/v1/sessions/" + fixture.id() + "/end")
            .header("Authorization", fixture.token())
            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", version)
            .contentType(MediaType.APPLICATION_JSON).content("{\"accumulatedActiveMs\":58000}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"))
        .andExpect(jsonPath("$.data.accumulatedActiveMs").value(58000));
  }

  @Test
  void headersAuthenticationAndOwnershipAreRequired() throws Exception {
    var fixture = started();
    String path = "/api/v1/sessions/" + fixture.id() + "/end";
    mvc.perform(post(path)).andExpect(status().isUnauthorized());
    mvc.perform(post(path).header("Authorization", fixture.token()))
        .andExpect(status().isBadRequest());
    mvc.perform(post(path).header("Authorization", fixture.token())
            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", "-1"))
        .andExpect(status().isBadRequest());
    var stranger = desktop("end-stranger-" + UUID.randomUUID());
    mvc.perform(post(path).header("Authorization", "Bearer " + stranger.accessToken())
            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", 0)
            .contentType(MediaType.APPLICATION_JSON).content("{\"accumulatedActiveMs\":0}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/sessions/missing/end").header("Authorization", fixture.token())
            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", 0)
            .contentType(MediaType.APPLICATION_JSON).content("{\"accumulatedActiveMs\":0}"))
        .andExpect(status().isNotFound());
    org.junit.jupiter.api.Assertions.assertEquals("RUNNING",
        sessions.findById(Long.valueOf(fixture.id())).orElseThrow().getStatus().name());
  }

  @Test
  void extensionAndAnotherDesktopCannotEndRunnerSession() throws Exception {
    var fixture = started();
    var session = sessions.findById(Long.valueOf(fixture.id())).orElseThrow();
    long userId = session.getUser().getId();
    long deviceId = session.getRunnerDevice().getId();
    var extension = new com.capstone.backend.auth.security.FocusOnPrincipal(userId,
        deviceId, 1,
        com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType.CHROME_EXTENSION);
    var rejected = org.junit.jupiter.api.Assertions.assertThrows(
        com.capstone.backend.common.api.ApiException.class,
        () -> commands.end(extension, fixture.id(), UUID.randomUUID().toString(), "0",
            new com.capstone.backend.session.dto.SessionDtos.EndRequest(
                java.math.BigDecimal.ZERO)));
    org.junit.jupiter.api.Assertions.assertEquals(
        com.capstone.backend.common.api.ApiErrorCode.AUTH_RELOGIN_REQUIRED, rejected.code());
    var otherDesktop = new com.capstone.backend.auth.security.FocusOnPrincipal(userId,
        deviceId + 10000, 1,
        com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType.DESKTOP);
    var missing = org.junit.jupiter.api.Assertions.assertThrows(
        com.capstone.backend.common.api.ApiException.class,
        () -> commands.end(otherDesktop, fixture.id(), UUID.randomUUID().toString(), "0",
            new com.capstone.backend.session.dto.SessionDtos.EndRequest(
                java.math.BigDecimal.ZERO)));
    org.junit.jupiter.api.Assertions.assertEquals(
        com.capstone.backend.common.api.ApiErrorCode.SESSION_NOT_FOUND, missing.code());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"different", "same", "resume"})
  void simultaneousCommandsMaintainOneTransition(String mode) throws Exception {
    var fixture = started();
    if (mode.equals("resume")) {
      jdbc.update("UPDATE learning_sessions SET status = 'PAUSED' WHERE id = ?",
          Long.valueOf(fixture.id()));
    }
    String sharedKey = UUID.randomUUID().toString();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var requests = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new AssertionError("start timeout");
        }
        if (mode.equals("resume") && i == 1) {
          return mvc.perform(post("/api/v1/sessions/" + fixture.id() + "/resume")
                  .header("Authorization", fixture.token())
                  .header("Idempotency-Key", UUID.randomUUID())
                  .header("If-Match-Version", fixture.version())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"readiness\":{\"screenAnalysisReady\":true}}"))
              .andReturn().getResponse().getStatus();
        }
        if (mode.equals("resume")) {
          return mvc.perform(post("/api/v1/sessions/" + fixture.id() + "/end")
                  .header("Authorization", fixture.token())
                  .header("Idempotency-Key", sharedKey)
                  .header("If-Match-Version", fixture.version())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"accumulatedActiveMs\":0}"))
              .andReturn().getResponse().getStatus();
        }
        return end(fixture, mode.equals("same") ? sharedKey : UUID.randomUUID().toString(),
            fixture.version())
            .andReturn().getResponse().getStatus();
      })).toList();
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      var results = new java.util.ArrayList<Integer>();
      for (var request : requests) {
        results.add(request.get(20, TimeUnit.SECONDS));
      }
      java.util.Collections.sort(results);
      org.junit.jupiter.api.Assertions.assertEquals(mode.equals("same")
          ? java.util.List.of(200, 200) : java.util.List.of(200, 409), results);
    }
    org.junit.jupiter.api.Assertions.assertEquals(fixture.version() + 1,
        sessions.findById(Long.valueOf(fixture.id())).orElseThrow().getVersion());
  }

  @Test
  void runningThirtyMinutesIsSavedAndReplayedWithoutTimeLoss() throws Exception {
    var fixture = started();
    jdbc.update("UPDATE learning_sessions SET last_resumed_at = ? WHERE id = ?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1800)),
        Long.valueOf(fixture.id()));
    String key = UUID.randomUUID().toString();
    for (int attempt = 0; attempt < 2; attempt++) {
      mvc.perform(post("/api/v1/sessions/" + fixture.id() + "/end")
              .header("Authorization", fixture.token()).header("Idempotency-Key", key)
              .header("If-Match-Version", fixture.version()).contentType(MediaType.APPLICATION_JSON)
              .content("{\"accumulatedActiveMs\":1800000}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.accumulatedActiveMs").value(1800000));
    }
    org.junit.jupiter.api.Assertions.assertEquals(1800000,
        sessions.findById(Long.valueOf(fixture.id())).orElseThrow().getAccumulatedActiveMs());
    mvc.perform(post("/api/v1/sessions/" + fixture.id() + "/end")
            .header("Authorization", fixture.token()).header("Idempotency-Key", key)
            .header("If-Match-Version", fixture.version()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accumulatedActiveMs\":1799999}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "", "{}", "{\"accumulatedActiveMs\":null}", "{\"accumulatedActiveMs\":-1}",
      "{\"accumulatedActiveMs\":1.5}", "{\"accumulatedActiveMs\":9223372036854775808}",
      "{\"accumulatedActiveMs\":1800000}"
  })
  void invalidFinalTimeCannotCommitEnd(String body) throws Exception {
    var fixture = started();
    mvc.perform(post("/api/v1/sessions/" + fixture.id() + "/end")
            .header("Authorization", fixture.token()).header("Idempotency-Key", UUID.randomUUID())
            .header("If-Match-Version", fixture.version()).contentType(MediaType.APPLICATION_JSON)
            .content(body)).andExpect(status().isBadRequest());
    var stored = sessions.findById(Long.valueOf(fixture.id())).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("RUNNING", stored.getStatus().name());
    org.junit.jupiter.api.Assertions.assertNull(stored.getEndedAt());
    org.junit.jupiter.api.Assertions.assertEquals(0, stored.getAccumulatedActiveMs());
    org.junit.jupiter.api.Assertions.assertEquals(fixture.version(), stored.getVersion());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"RUNNING", "PAUSED", "AUTO_PAUSED"})
  void finalTimeCannotDecreaseOrIncreaseWhilePaused(String state) throws Exception {
    var fixture = started();
    jdbc.update("UPDATE learning_sessions SET status = ?, accumulated_active_ms = 2000"
        + " WHERE id = ?", state, Long.valueOf(fixture.id()));
    end(fixture, UUID.randomUUID().toString(), fixture.version())
        .andExpect(status().isBadRequest());
    if (!state.equals("RUNNING")) {
      jdbc.update("UPDATE learning_sessions SET accumulated_active_ms = 1000 WHERE id = ?",
          Long.valueOf(fixture.id()));
      end(fixture, UUID.randomUUID().toString(), fixture.version())
          .andExpect(status().isBadRequest());
    }
    org.junit.jupiter.api.Assertions.assertEquals(state,
        sessions.findById(Long.valueOf(fixture.id())).orElseThrow().getStatus().name());
  }

  private org.springframework.test.web.servlet.ResultActions end(
      Fixture fixture, String key, long version) throws Exception {
    return mvc.perform(post("/api/v1/sessions/" + fixture.id() + "/end")
        .header("Authorization", fixture.token()).header("Idempotency-Key", key)
        .header("If-Match-Version", version).contentType(MediaType.APPLICATION_JSON)
        .content("{\"accumulatedActiveMs\":1234}"));
  }

  private Fixture started() throws Exception {
    var owner = desktop("end-" + UUID.randomUUID());
    String token = "Bearer " + owner.accessToken();
    String goal = mvc.perform(post("/api/v1/goals").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"종료 검증\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String goalId = json.readTree(goal).path("data").path("goalId").asText();
    var response = mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON).content(request(goalId, 1800000)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    var data = json.readTree(response).path("data");
    return new Fixture(data.path("sessionId").asText(), data.path("version").asLong(), token,
        owner.device().deviceId());
  }

  private record Fixture(String id, long version, String token, String deviceId) {}

  private String request(String goalId, long duration) {
    return "{\"goalId\":\"" + goalId + "\",\"targetDurationMs\":" + duration
        + ",\"timeZone\":\"Asia/Seoul\",\"sessionOptions\":{\"interventionLevel\":\"GENTLE\","
        + "\"notificationChannels\":[\"MOLE_UI\"],\"cameraAnalysisEnabled\":false},"
        + "\"startReadiness\":{\"screenAnalysisReady\":true}}";
  }

  private LoginResponse desktop(String installationId) {
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), installationId,
        "http://127.0.0.1:49152", new DesktopDevice(installationId, "QA", "MACOS")));
  }
}
