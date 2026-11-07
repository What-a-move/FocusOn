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
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class SessionApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private LearningSessionRepository sessions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
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

  @Test
  void extendIsIdempotentAndValidatesDurationAndOwnership() throws Exception {
    LoginResponse owner = desktop("extend-owner-" + UUID.randomUUID());
    final LoginResponse other = desktop("extend-other-" + UUID.randomUUID());
    String token = "Bearer " + owner.accessToken();
    String id = startForExtend(owner);
    long version = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
    String path = "/api/v1/sessions/" + id + "/extend";
    String key = UUID.randomUUID().toString();
    String body = "{\"additionalDurationMs\":600000}";
    String first = mvc.perform(post(path).header("Authorization", token)
            .header("Idempotency-Key", key).header("If-Match-Version", version)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"))
        .andExpect(jsonPath("$.data.targetDurationMs").value(2400000))
        .andReturn().getResponse().getContentAsString();
    String replay = mvc.perform(post(path).header("Authorization", token)
            .header("Idempotency-Key", key).header("If-Match-Version", version)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    org.junit.jupiter.api.Assertions.assertEquals(json.readTree(first), json.readTree(replay));
    org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
        "SELECT count(*) FROM session_extensions WHERE session_id = ?", Integer.class,
        Long.valueOf(id)));
    mvc.perform(post(path).header("Authorization", token)
            .header("Idempotency-Key", key).header("If-Match-Version", version)
            .contentType(MediaType.APPLICATION_JSON).content("{\"additionalDurationMs\":300000}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    mvc.perform(post(path).header("Authorization", token)
            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", version)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_VERSION_CONFLICT"));
    long currentVersion = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
    for (String invalid : new String[] {"0", "299999", "360001", "18060000", "300000.5"}) {
      mvc.perform(post(path).header("Authorization", token)
              .header("Idempotency-Key", UUID.randomUUID())
              .header("If-Match-Version", currentVersion).contentType(MediaType.APPLICATION_JSON)
              .content("{\"additionalDurationMs\":" + invalid + "}"))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(post(path).header("Authorization", "Bearer " + other.accessToken())
            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", currentVersion)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    org.junit.jupiter.api.Assertions.assertEquals(2400000,
        sessions.findById(Long.valueOf(id)).orElseThrow().getTargetDurationMs());
  }

  @Test
  void extendResumesOnlyAwaitingAndPreservesPausedStates() throws Exception {
    for (String statusName : new String[] {"PAUSED", "AUTO_PAUSED", "AWAITING_END_CONFIRMATION"}) {
      LoginResponse owner = desktop("extend-state-" + UUID.randomUUID());
      String id = startForExtend(owner);
      long numericId = Long.parseLong(id);
      jdbc.update("UPDATE learning_sessions SET status = ?, accumulated_active_ms = ?, "
          + "last_resumed_at = NULL WHERE id = ?", statusName, 1000L, numericId);
      var before = sessions.findById(numericId).orElseThrow();
      String expected = statusName.equals("AWAITING_END_CONFIRMATION") ? "RUNNING" : statusName;
      mvc.perform(post("/api/v1/sessions/" + id + "/extend")
              .header("Authorization", "Bearer " + owner.accessToken())
              .header("Idempotency-Key", UUID.randomUUID())
              .header("If-Match-Version", before.getVersion())
              .contentType(MediaType.APPLICATION_JSON).content("{\"additionalDurationMs\":300000}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(expected))
          .andExpect(jsonPath("$.data.targetDurationMs").value(2100000));
      var stored = sessions.findById(numericId).orElseThrow();
      org.junit.jupiter.api.Assertions.assertEquals(before.getVersion() + 1, stored.getVersion());
      if (expected.equals("RUNNING")) {
        org.junit.jupiter.api.Assertions.assertNotNull(stored.getLastResumedAt());
        org.junit.jupiter.api.Assertions.assertEquals(1000, stored.getAccumulatedAtResumeMs());
      } else {
        org.junit.jupiter.api.Assertions.assertNull(stored.getLastResumedAt());
      }
    }
  }

  @Test
  void concurrentExtensionsWithSameVersionOnlyOneCommits() throws Exception {
    LoginResponse owner = desktop("extend-race-" + UUID.randomUUID());
    String id = startForExtend(owner);
    long version = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("연장 동시 요청 대기 시간 초과");
        }
        return mvc.perform(post("/api/v1/sessions/" + id + "/extend")
                .header("Authorization", "Bearer " + owner.accessToken())
                .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", version)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"additionalDurationMs\":300000}"))
            .andReturn().getResponse().getStatus();
      })).toList();
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      var statuses = new java.util.ArrayList<Integer>();
      for (var result : results) {
        statuses.add(result.get(20, TimeUnit.SECONDS));
      }
      java.util.Collections.sort(statuses);
      org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(200, 409), statuses);
    }
    org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
        "SELECT count(*) FROM session_extensions WHERE session_id = ?", Integer.class,
        Long.valueOf(id)));
    org.junit.jupiter.api.Assertions.assertEquals(2100000,
        sessions.findById(Long.valueOf(id)).orElseThrow().getTargetDurationMs());
  }

  @Test
  void postgresConcurrentExtensionsActuallyWaitForUserLock() throws Exception {
    try (var blocker = dataSource.getConnection()) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          "PostgreSQL".equals(blocker.getMetaData().getDatabaseProductName()));
      LoginResponse owner = desktop("extend-lock-" + UUID.randomUUID());
      String id = startForExtend(owner);
      long version = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
      blocker.setAutoCommit(false);
      int blockerPid;
      try (var statement = blocker.prepareStatement("SELECT pg_backend_pid()");
          var rows = statement.executeQuery()) {
        rows.next();
        blockerPid = rows.getInt(1);
      }
      try (var statement = blocker.prepareStatement(
          "SELECT id FROM users WHERE id = ? FOR UPDATE")) {
        statement.setLong(1, Long.parseLong(owner.user().userId()));
        statement.executeQuery().close();
      }
      try (var executor = Executors.newFixedThreadPool(2)) {
        var results = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() ->
            mvc.perform(post("/api/v1/sessions/" + id + "/extend")
                    .header("Authorization", "Bearer " + owner.accessToken())
                    .header("Idempotency-Key", UUID.randomUUID())
                    .header("If-Match-Version", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"additionalDurationMs\":300000}"))
                .andReturn().getResponse().getStatus())).toList();
        try {
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
          int blocked = 0;
          while (System.nanoTime() < deadline && blocked < 2) {
            blocked = jdbc.queryForObject("WITH RECURSIVE waiting(pid) AS ("
                + "SELECT CAST(? AS integer) UNION "
                + "SELECT a.pid FROM pg_stat_activity a JOIN waiting w "
                + "ON w.pid = ANY(pg_blocking_pids(a.pid))) "
                + "SELECT count(*) - 1 FROM waiting", Integer.class, blockerPid);
            if (blocked < 2) {
              Thread.sleep(20);
            }
          }
          org.junit.jupiter.api.Assertions.assertTrue(blocked >= 2,
              "연장 요청 두 건이 실제 잠금을 기다려야 한다");
        } finally {
          blocker.commit();
        }
        var statuses = new java.util.ArrayList<Integer>();
        for (var result : results) {
          statuses.add(result.get(20, TimeUnit.SECONDS));
        }
        java.util.Collections.sort(statuses);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(200, 409), statuses);
      }
      org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
          "SELECT count(*) FROM session_extensions WHERE session_id = ?", Integer.class,
          Long.valueOf(id)));
    }
  }

  @Test
  void terminalSessionCannotBeExtended() throws Exception {
    for (String terminal : new String[] {"ENDING", "ENDED", "ABANDONED"}) {
      LoginResponse owner = desktop("extend-terminal-" + UUID.randomUUID());
      String id = startForExtend(owner);
      long numericId = Long.parseLong(id);
      jdbc.update("UPDATE learning_sessions SET status = ? WHERE id = ?", terminal, numericId);
      long version = sessions.findById(numericId).orElseThrow().getVersion();
      mvc.perform(post("/api/v1/sessions/" + id + "/extend")
              .header("Authorization", "Bearer " + owner.accessToken())
              .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", version)
              .contentType(MediaType.APPLICATION_JSON).content("{\"additionalDurationMs\":300000}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
      org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
          "SELECT count(*) FROM session_extensions WHERE session_id = ?", Integer.class,
          numericId));
    }
  }

  private String startForExtend(LoginResponse owner) throws Exception {
    String token = "Bearer " + owner.accessToken();
    String goal = mvc.perform(post("/api/v1/goals").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"extend goal\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String goalId = json.readTree(goal).path("data").path("goalId").asText();
    String started = mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
            .content(request(goalId, 1800000)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(started).path("data").path("sessionId").asText();
  }

  @Test
  void startRetryConflictAndUserIsolation() throws Exception {
    LoginResponse owner = desktop("session-owner");
    final LoginResponse other = desktop("session-other");
    String token = "Bearer " + owner.accessToken();
    String goalResponse = mvc.perform(post("/api/v1/goals").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"세션 목표\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String goalId = json.readTree(goalResponse).path("data").path("goalId").asText();
    String body = request(goalId, 1800000);
    String key = UUID.randomUUID().toString();
    String deviceId = owner.device().deviceId();
    mvc.perform(get("/api/v1/sessions/active").header("Authorization", token))
        .andExpect(status().isNoContent());
    String created = mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("RUNNING"))
        .andReturn().getResponse().getContentAsString();
    String id = json.readTree(created).path("data").path("sessionId").asText();
    var stored = sessions.findById(Long.parseLong(id)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("세션 목표", stored.getGoalTextSnapshot());
    org.junit.jupiter.api.Assertions.assertEquals("Asia/Seoul", stored.getUserTimeZoneSnapshot());
    org.junit.jupiter.api.Assertions.assertEquals(stored.getStartedAt().atZone(
        java.time.ZoneId.of("Asia/Seoul")).toLocalDate(), stored.getStudyDate());
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.data.sessionId").value(id));
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(request(goalId, 2400000)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ACTIVE_EXISTS"));
    mvc.perform(get("/api/v1/sessions/active").header("Authorization", token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value(id));
    mvc.perform(get("/api/v1/sessions/active")
            .header("Authorization", "Bearer " + other.accessToken()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/sessions/active")).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidHeadersDurationAndReadinessAreRejected() throws Exception {
    LoginResponse owner = desktop("session-invalid");
    String token = "Bearer " + owner.accessToken();
    String key = UUID.randomUUID().toString();
    String deviceId = owner.device().deviceId();
    String body = request("1", 1800000);
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(request("1", 299999)))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("1800000", "1800000.5")))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("[\"MOLE_UI\"]", "[\"MOLE_UI\",\"MOLE_UI\"]")))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("\"screenAnalysisReady\":true", "\"screenAnalysisReady\":false")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_START_REQUIREMENT_MISSING"));
    mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", deviceId).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("\"cameraAnalysisEnabled\":false",
                "\"cameraAnalysisEnabled\":true")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_START_REQUIREMENT_MISSING"));
  }

  @Test
  void anotherUsersGoalAndAnotherDeviceHeaderAreRejected() throws Exception {
    LoginResponse owner = desktop("session-goal-owner");
    LoginResponse other = desktop("session-goal-other");
    String ownerToken = "Bearer " + owner.accessToken();
    String goalResponse = mvc.perform(post("/api/v1/goals").header("Authorization", ownerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"본인 목표\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String goalId = json.readTree(goalResponse).path("data").path("goalId").asText();
    String key = UUID.randomUUID().toString();
    mvc.perform(post("/api/v1/sessions")
            .header("Authorization", "Bearer " + other.accessToken())
            .header("X-Device-Id", other.device().deviceId()).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(request(goalId, 1800000)))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
    mvc.perform(post("/api/v1/sessions").header("Authorization", ownerToken)
            .header("X-Device-Id", other.device().deviceId()).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(request(goalId, 1800000)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void concurrentPauseAndResumeMaintainVersionAndIdempotency() throws Exception {
    LoginResponse owner = desktop("session-command-" + UUID.randomUUID());
    String token = "Bearer " + owner.accessToken();
    String goalBody = mvc.perform(post("/api/v1/goals").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"동시성 목표\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String goalId = json.readTree(goalBody).path("data").path("goalId").asText();
    String started = mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
            .content(request(goalId, 1800000)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String sessionId = json.readTree(started).path("data").path("sessionId").asText();
    long version = json.readTree(started).path("data").path("version").asLong();
    String pauseBody = "{\"pauseReason\":\"USER\",\"accumulatedActiveMs\":1000}";
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("일시정지 동시 요청 대기 시간 초과");
        }
        return mvc.perform(post("/api/v1/sessions/" + sessionId + "/pause")
                .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID())
                .header("If-Match-Version", version).contentType(MediaType.APPLICATION_JSON)
                .content(pauseBody)).andReturn().getResponse().getStatus();
      })).toList();
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      var statuses = new java.util.ArrayList<Integer>();
      for (var future : futures) {
        statuses.add(future.get(20, TimeUnit.SECONDS));
      }
      java.util.Collections.sort(statuses);
      org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(200, 409), statuses);
    }
    var paused = sessions.findById(Long.valueOf(sessionId)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(1000, paused.getAccumulatedActiveMs());
    org.junit.jupiter.api.Assertions.assertEquals(version + 1, paused.getVersion());
    String resumeKey = UUID.randomUUID().toString();
    String resumeBody = "{\"readiness\":{\"screenAnalysisReady\":true}}";
    String path = "/api/v1/sessions/" + sessionId + "/resume";
    CountDownLatch resumeReady = new CountDownLatch(2);
    CountDownLatch resumeStart = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        resumeReady.countDown();
        if (!resumeStart.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("재개 동시 요청 대기 시간 초과");
        }
        return mvc.perform(post(path).header("Authorization", token)
                .header("Idempotency-Key", resumeKey).header("If-Match-Version", version + 1)
                .contentType(MediaType.APPLICATION_JSON).content(resumeBody))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      })).toList();
      org.junit.jupiter.api.Assertions.assertTrue(resumeReady.await(10, TimeUnit.SECONDS));
      resumeStart.countDown();
      var first = json.readTree(futures.get(0).get(20, TimeUnit.SECONDS));
      var second = json.readTree(futures.get(1).get(20, TimeUnit.SECONDS));
      org.junit.jupiter.api.Assertions.assertEquals(first, second);
      org.junit.jupiter.api.Assertions.assertEquals("RUNNING", first.path("data").path("status")
          .asText());
    }
    var after = sessions.findById(Long.valueOf(sessionId)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(version + 2, after.getVersion());
    org.junit.jupiter.api.Assertions.assertEquals(1000, after.getAccumulatedAtResumeMs());
  }

  @Test
  void simultaneousStartsCreateOnlyOneActiveSession() throws Exception {
    LoginResponse owner = desktop("session-concurrent");
    String token = "Bearer " + owner.accessToken();
    String goalResponse = mvc.perform(post("/api/v1/goals").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"동시 시작\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String goalId = json.readTree(goalResponse).path("data").path("goalId").asText();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
        }
        return mvc.perform(post("/api/v1/sessions").header("Authorization", token)
                .header("X-Device-Id", owner.device().deviceId())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(request(goalId, 1800000)))
            .andReturn();
      })).toList();
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      var results = new java.util.ArrayList<MvcResult>();
      for (var future : futures) {
        results.add(future.get(20, TimeUnit.SECONDS));
      }
      var statuses = results.stream().map(result -> result.getResponse().getStatus())
          .sorted().toList();
      org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(201, 409), statuses);
      var conflict = results.stream().filter(result -> result.getResponse().getStatus() == 409)
          .findFirst().orElseThrow();
      org.junit.jupiter.api.Assertions.assertEquals("SESSION_ACTIVE_EXISTS",
          json.readTree(conflict.getResponse().getContentAsString()).path("code").asText());
    }
  }

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
