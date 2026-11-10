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
import com.capstone.backend.auth.dto.PairingDtos.ClaimRequest;
import com.capstone.backend.auth.dto.PairingDtos.LinkedExtension;
import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.persistence.UserEntity.OnboardingStatus;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.common.api.RequestIdFilter;
import com.capstone.backend.report.dto.LearningSummaryResponse;
import com.capstone.backend.report.persistence.LearningSummaryEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.service.SessionHeartbeatCleanup;
import com.capstone.backend.user.service.OnboardingReadinessService;
import com.capstone.backend.user.service.OnboardingReadinessService.Readiness;
import com.capstone.backend.user.service.OnboardingReadinessService.Steps;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
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
  @Autowired private com.capstone.backend.auth.service.PairingService pairing;
  @Autowired private com.capstone.backend.report.persistence.LearningSummaryRepository summaries;
  @Autowired private ObjectMapper json;
  @Autowired private LearningSessionRepository sessions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionHeartbeatCleanup heartbeatCleanup;
  @Autowired private DataSource dataSource;
  @Autowired private org.springframework.transaction.support.TransactionTemplate transactions;
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
  void concurrentExtendAndPauseWithSameVersionOnlyOneCommits() throws Exception {
    LoginResponse owner = desktop("extend-pause-race-" + UUID.randomUUID());
    String id = startForExtend(owner);
    long version = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("연장과 일시정지 동시 요청 대기 시간 초과");
        }
        String command = index == 0 ? "extend" : "pause";
        String body = index == 0 ? "{\"additionalDurationMs\":300000}"
            : "{\"pauseReason\":\"USER\",\"accumulatedActiveMs\":0}";
        return mvc.perform(post("/api/v1/sessions/" + id + "/" + command)
                .header("Authorization", "Bearer " + owner.accessToken())
                .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", version)
                .contentType(MediaType.APPLICATION_JSON).content(body))
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
    var stored = sessions.findById(Long.valueOf(id)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(version + 1, stored.getVersion());
    int extensionCount = jdbc.queryForObject(
        "SELECT count(*) FROM session_extensions WHERE session_id = ?", Integer.class,
        Long.valueOf(id));
    org.junit.jupiter.api.Assertions.assertEquals(extensionCount == 1 ? 2100000 : 1800000,
        stored.getTargetDurationMs());
    org.junit.jupiter.api.Assertions.assertEquals(extensionCount == 1 ? "RUNNING" : "PAUSED",
        stored.getStatus().name());
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
  void heartbeatEnforcesOrderingAndPreservesCommandVersion() throws Exception {
    LoginResponse owner = desktop("heartbeat-" + UUID.randomUUID());
    final LoginResponse other = desktop("heartbeat-other-" + UUID.randomUUID());
    String token = "Bearer " + owner.accessToken();
    String sessionId = startForHeartbeat(owner);
    long version = sessions.findById(Long.valueOf(sessionId)).orElseThrow().getVersion();
    Instant observed = Instant.now().minusSeconds(3);
    String first = heartbeatBody(observed, 1000);
    String path = "/api/v1/sessions/" + sessionId + "/heartbeats";
    mvc.perform(post(path).header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(first))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.accumulatedActiveMs").value(1000));
    var stored = sessions.findById(Long.valueOf(sessionId)).orElseThrow();
    var received = stored.getLastHeartbeatAt();
    org.junit.jupiter.api.Assertions.assertEquals(version, stored.getVersion());
    mvc.perform(post(path).header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(heartbeatBody(observed, 2000)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.accumulatedActiveMs").value(1000));
    org.junit.jupiter.api.Assertions.assertEquals(received,
        sessions.findById(Long.valueOf(sessionId)).orElseThrow().getLastHeartbeatAt());
    mvc.perform(post(path).header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(heartbeatBody(observed.plusSeconds(1), 999)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SESSION_HEARTBEAT_INVALID"));
    mvc.perform(post(path).header("Authorization", "Bearer " + other.accessToken())
            .header("X-Device-Id", other.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(first))
        .andExpect(status().isNotFound());
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(first))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void heartbeatAtExpiredDeadlineCommitsAbandonedWithoutAddingTime() throws Exception {
    LoginResponse owner = desktop("heartbeat-expire-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    jdbc.update("UPDATE learning_sessions SET last_resumed_at = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(181)), Long.valueOf(id));
    mvc.perform(post("/api/v1/sessions/" + id + "/heartbeats")
            .header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(heartbeatBody(Instant.now(), 1000)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_ABANDONED"))
        .andExpect(jsonPath("$.details.endReason").value("HEARTBEAT_TIMEOUT"));
    var stored = sessions.findById(Long.valueOf(id)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("ABANDONED", stored.getStatus().name());
    org.junit.jupiter.api.Assertions.assertEquals(0, stored.getAccumulatedActiveMs());
    org.junit.jupiter.api.Assertions.assertNotNull(stored.getEndedAt());
  }

  @Test
  void heartbeatUsesAuthenticatedRateLimitAndRejectsFutureClock() throws Exception {
    LoginResponse owner = desktop("heartbeat-limit-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    String path = "/api/v1/sessions/" + id + "/heartbeats";
    String token = "Bearer " + owner.accessToken();
    String device = owner.device().deviceId();
    mvc.perform(post(path).header("Authorization", token).header("X-Device-Id", device)
            .contentType(MediaType.APPLICATION_JSON)
            .content(heartbeatBody(Instant.now().plusSeconds(60), 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SESSION_HEARTBEAT_INVALID"));
    boolean limited = false;
    for (int i = 0; i < 60; i++) {
      var result = mvc.perform(post(path).header("Authorization", token)
              .header("X-Device-Id", device).contentType(MediaType.APPLICATION_JSON)
              .content(heartbeatBody(Instant.now(), 0)))
          .andReturn().getResponse();
      if (result.getStatus() == 429) {
        org.junit.jupiter.api.Assertions.assertNotNull(result.getHeader("Retry-After"));
        limited = true;
        break;
      }
      org.junit.jupiter.api.Assertions.assertEquals(200, result.getStatus());
    }
    org.junit.jupiter.api.Assertions.assertTrue(limited);
  }

  @Test
  void cleanupDistinguishesCandidateFromFinalAndPreservesLatestTime() throws Exception {
    LoginResponse owner = desktop("heartbeat-worker-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    long numericId = Long.parseLong(id);
    jdbc.update("UPDATE learning_sessions SET last_resumed_at = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(65)), numericId);
    heartbeatCleanup.cleanup();
    org.junit.jupiter.api.Assertions.assertEquals("RUNNING",
        sessions.findById(numericId).orElseThrow().getStatus().name());
    jdbc.update("UPDATE learning_sessions SET last_resumed_at = ?, accumulated_active_ms = ? "
            + "WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(181)),
        5000L, numericId);
    heartbeatCleanup.cleanup();
    var stored = sessions.findById(numericId).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("ABANDONED", stored.getStatus().name());
    org.junit.jupiter.api.Assertions.assertEquals(5000, stored.getAccumulatedActiveMs());
    org.junit.jupiter.api.Assertions.assertEquals("HEARTBEAT_TIMEOUT", stored.getEndReason());
  }

  @Test
  void reachingTargetTransitionsAndIncrementsVersion() throws Exception {
    LoginResponse owner = desktop("heartbeat-target-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    long numericId = Long.parseLong(id);
    long version = sessions.findById(numericId).orElseThrow().getVersion();
    jdbc.update("UPDATE learning_sessions SET last_resumed_at = ?, last_heartbeat_at = ? "
            + "WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(300)),
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)), numericId);
    mvc.perform(post("/api/v1/sessions/" + id + "/heartbeats")
            .header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(heartbeatBody(Instant.now(), 300000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("AWAITING_END_CONFIRMATION"));
    var stored = sessions.findById(numericId).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(version + 1, stored.getVersion());
    org.junit.jupiter.api.Assertions.assertEquals(300000, stored.getAccumulatedActiveMs());
  }

  @Test
  void concurrentOutOfOrderHeartbeatsNeverMoveTheConfirmedTimeBackwards() throws Exception {
    LoginResponse owner = desktop("heartbeat-race-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    Instant now = Instant.now().minusSeconds(3);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("동시 heartbeat 요청 대기 시간 초과");
        }
        return mvc.perform(post("/api/v1/sessions/" + id + "/heartbeats")
                .header("Authorization", "Bearer " + owner.accessToken())
                .header("X-Device-Id", owner.device().deviceId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(heartbeatBody(now.plusSeconds(index), 1000L + index * 1000L)))
            .andReturn().getResponse().getStatus();
      })).toList();
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      for (var result : results) {
        org.junit.jupiter.api.Assertions.assertEquals(200, result.get(20, TimeUnit.SECONDS));
      }
    }
    var stored = sessions.findById(Long.valueOf(id)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(2000, stored.getAccumulatedActiveMs());
  }

  @Test
  void concurrentCleanupAndExpiredHeartbeatCannotReviveSession() throws Exception {
    LoginResponse owner = desktop("heartbeat-cleanup-race-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    long numericId = Long.parseLong(id);
    jdbc.update("UPDATE learning_sessions SET last_resumed_at = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(181)), numericId);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var heartbeat = executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("heartbeat 경쟁 대기 시간 초과");
        }
        return mvc.perform(post("/api/v1/sessions/" + id + "/heartbeats")
                .header("Authorization", "Bearer " + owner.accessToken())
                .header("X-Device-Id", owner.device().deviceId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(heartbeatBody(Instant.now(), 1000)))
            .andReturn().getResponse().getStatus();
      });
      final var cleanup = executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("자동 종료 경쟁 대기 시간 초과");
        }
        heartbeatCleanup.cleanup();
        return true;
      });
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      org.junit.jupiter.api.Assertions.assertEquals(409, heartbeat.get(20, TimeUnit.SECONDS));
      org.junit.jupiter.api.Assertions.assertTrue(cleanup.get(20, TimeUnit.SECONDS));
    }
    var stored = sessions.findById(numericId).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("ABANDONED", stored.getStatus().name());
    org.junit.jupiter.api.Assertions.assertEquals(0, stored.getAccumulatedActiveMs());
  }

  @Test
  void postgresCleanupSkipsLockedOwnerAndProcessesOtherSessions() throws Exception {
    try (var blocker = dataSource.getConnection()) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          "PostgreSQL".equals(blocker.getMetaData().getDatabaseProductName()));
      LoginResponse blockedOwner = desktop("heartbeat-blocked-" + UUID.randomUUID());
      String blockedId = startForHeartbeat(blockedOwner);
      LoginResponse otherOwner = desktop("heartbeat-other-cleanup-" + UUID.randomUUID());
      String otherId = startForHeartbeat(otherOwner);
      jdbc.update("UPDATE learning_sessions SET last_resumed_at = ? WHERE id IN (?, ?)",
          java.sql.Timestamp.from(Instant.now().minusSeconds(181)),
          Long.valueOf(blockedId), Long.valueOf(otherId));
      blocker.setAutoCommit(false);
      try (var statement = blocker.prepareStatement(
          "SELECT id FROM users WHERE id = ? FOR UPDATE")) {
        statement.setLong(1, Long.parseLong(blockedOwner.user().userId()));
        statement.executeQuery().close();
      }
      try (var executor = Executors.newSingleThreadExecutor()) {
        var cleanup = executor.submit(() -> {
          heartbeatCleanup.cleanup();
          return true;
        });
        try {
          org.junit.jupiter.api.Assertions.assertTrue(cleanup.get(10, TimeUnit.SECONDS));
          org.junit.jupiter.api.Assertions.assertEquals("RUNNING", sessions.findById(
              Long.valueOf(blockedId)).orElseThrow().getStatus().name());
          org.junit.jupiter.api.Assertions.assertEquals("ABANDONED", sessions.findById(
              Long.valueOf(otherId)).orElseThrow().getStatus().name());
        } finally {
          blocker.commit();
        }
      }
      heartbeatCleanup.cleanup();
      org.junit.jupiter.api.Assertions.assertEquals("ABANDONED", sessions.findById(
          Long.valueOf(blockedId)).orElseThrow().getStatus().name());
    }
  }

  @Test
  void postgresCleanupPreservesHeartbeatCommittingAfterCandidateSelection() throws Exception {
    try (var connection = dataSource.getConnection()) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()));
      LoginResponse owner = desktop("heartbeat-in-flight-" + UUID.randomUUID());
      long id = Long.parseLong(startForHeartbeat(owner));
      connection.setAutoCommit(false);
      try (var lock = connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
        lock.setLong(1, Long.parseLong(owner.user().userId()));
        lock.executeQuery().close();
      }
      Instant cutoff = Instant.now().minusSeconds(180);
      jdbc.update("UPDATE learning_sessions SET last_resumed_at = ? WHERE id = ?",
          java.sql.Timestamp.from(cutoff.minusSeconds(1)), id);
      try (var update = connection.prepareStatement(
          "UPDATE learning_sessions SET last_heartbeat_at = ? WHERE id = ?")) {
        update.setTimestamp(1, java.sql.Timestamp.from(Instant.now()));
        update.setLong(2, id);
        update.executeUpdate();
      }
      try {
        var candidates = sessions.findExpiredCandidates(cutoff, 0,
            org.springframework.data.domain.PageRequest.of(0, 100));
        org.junit.jupiter.api.Assertions.assertTrue(candidates.contains(id));
        try (var executor = Executors.newSingleThreadExecutor()) {
          var cleanup = executor.submit(() -> {
            heartbeatCleanup.cleanup();
            return true;
          });
          org.junit.jupiter.api.Assertions.assertTrue(cleanup.get(10, TimeUnit.SECONDS));
        }
        connection.commit();
      } finally {
        connection.rollback();
      }
      heartbeatCleanup.cleanup();
      var stored = sessions.findById(id).orElseThrow();
      org.junit.jupiter.api.Assertions.assertEquals("RUNNING", stored.getStatus().name());
      org.junit.jupiter.api.Assertions.assertNotNull(stored.getLastHeartbeatAt());
    }
  }

  @Test
  void heartbeatRecoversAfterClockMovesBackwardWithNewProgress() throws Exception {
    LoginResponse owner = desktop("heartbeat-clock-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    String path = "/api/v1/sessions/" + id + "/heartbeats";
    String token = "Bearer " + owner.accessToken();
    Instant first = Instant.now();
    mvc.perform(post(path).header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(heartbeatBody(first, 1000)))
        .andExpect(status().isOk());
    Instant rollback = first.minusSeconds(10);
    mvc.perform(post(path).header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(heartbeatBody(rollback, 2000)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.accumulatedActiveMs").value(2000));
    Instant received = sessions.findById(Long.valueOf(id)).orElseThrow().getLastHeartbeatAt();
    mvc.perform(post(path).header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(heartbeatBody(rollback, 2000)))
        .andExpect(status().isOk());
    org.junit.jupiter.api.Assertions.assertEquals(received,
        sessions.findById(Long.valueOf(id)).orElseThrow().getLastHeartbeatAt());
  }

  @Test
  void cleanupRechecksHeartbeatAfterCandidateSelection() throws Exception {
    LoginResponse owner = desktop("heartbeat-recheck-" + UUID.randomUUID());
    long id = Long.parseLong(startForHeartbeat(owner));
    Instant cutoff = Instant.now().minusSeconds(180);
    jdbc.update("UPDATE learning_sessions SET last_resumed_at = ? WHERE id = ?",
        java.sql.Timestamp.from(cutoff.minusSeconds(1)), id);
    var candidateIds = sessions.findExpiredCandidates(cutoff, 0,
        org.springframework.data.domain.PageRequest.of(0, 100));
    org.junit.jupiter.api.Assertions.assertTrue(candidateIds.contains(id));
    jdbc.update("UPDATE learning_sessions SET last_heartbeat_at = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now()), id);
    transactions.executeWithoutResult(status -> org.junit.jupiter.api.Assertions.assertEquals(0,
        sessions.abandonIfExpired(id, cutoff, Instant.now())));
    org.junit.jupiter.api.Assertions.assertEquals("RUNNING",
        sessions.findById(id).orElseThrow().getStatus().name());
  }

  private String startForHeartbeat(LoginResponse owner) throws Exception {
    String token = "Bearer " + owner.accessToken();
    String goal = mvc.perform(post("/api/v1/goals").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"heartbeat goal\","
                + "\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String goalId = json.readTree(goal).path("data").path("goalId").asText();
    String started = mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
            .content(request(goalId, 300000)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(started).path("data").path("sessionId").asText();
  }

  private String heartbeatBody(Instant observed, long activeMs) {
    return "{\"clientObservedAt\":\"" + observed + "\",\"accumulatedActiveMs\":" + activeMs + "}";
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

  @Test
  void detailAndHistoryEnforceOwnershipAndPagination() throws Exception {
    LoginResponse owner = desktop("query-owner-" + UUID.randomUUID());
    LoginResponse other = desktop("query-other-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    String token = "Bearer " + owner.accessToken();
    String path = "/api/v1/sessions/" + id;
    mvc.perform(get(path).header("Authorization", token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value(id))
        .andExpect(jsonPath("$.data.interventionPolicyVersion").isString());
    mvc.perform(get(path).header("Authorization", "Bearer " + other.accessToken()))
        .andExpect(status().isNotFound());
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/sessions/bad").header("Authorization", token))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/sessions").header("Authorization", token)
            .param("from", "2000-01-01").param("to", "2099-12-31").param("limit", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].sessionId").value(id))
        .andExpect(jsonPath("$.data.nextCursor").isEmpty());
    mvc.perform(get("/api/v1/sessions").header("Authorization", token)
            .param("from", "2000-01-01").param("to", "2099-12-31").param("cursor", id))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    mvc.perform(get("/api/v1/sessions").header("Authorization", token)
            .param("from", "bad").param("to", "2099-12-31"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/sessions").header("Authorization", "Bearer " + other.accessToken())
            .param("from", "2000-01-01").param("to", "2099-12-31"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void concurrentQueriesDoNotModifySessionVersionOrState() throws Exception {
    LoginResponse owner = desktop("query-concurrent-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    var before = sessions.findById(Long.valueOf(id)).orElseThrow();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> mvc.perform(get("/api/v1/sessions/" + id)
          .header("Authorization", "Bearer " + owner.accessToken())).andReturn());
      var second = executor.submit(() -> mvc.perform(get("/api/v1/sessions")
          .header("Authorization", "Bearer " + owner.accessToken())
          .param("from", "2000-01-01").param("to", "2099-12-31")).andReturn());
      org.junit.jupiter.api.Assertions.assertEquals(200,
          first.get(10, TimeUnit.SECONDS).getResponse().getStatus());
      org.junit.jupiter.api.Assertions.assertEquals(200,
          second.get(10, TimeUnit.SECONDS).getResponse().getStatus());
    }
    var after = sessions.findById(Long.valueOf(id)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(before.getVersion(), after.getVersion());
    org.junit.jupiter.api.Assertions.assertEquals(before.getStatus(), after.getStatus());
    org.junit.jupiter.api.Assertions.assertEquals(before.getAccumulatedActiveMs(),
        after.getAccumulatedActiveMs());
  }

  @Test
  void summaryValidatesStateOwnershipAndPreservesConfirmedTime() throws Exception {
    LoginResponse owner = desktop("summary-owner-" + UUID.randomUUID());
    LoginResponse other = desktop("summary-other-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    String path = "/api/v1/sessions/" + id + "/summary";
    String token = "Bearer " + owner.accessToken();
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", "Bearer " + other.accessToken()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/sessions/bad/summary").header("Authorization", token))
        .andExpect(status().isNotFound());
    for (String state : new String[] {"RUNNING", "PAUSED", "AUTO_PAUSED",
        "AWAITING_END_CONFIRMATION", "ENDING"}) {
      jdbc.update("UPDATE learning_sessions SET status = ? WHERE id = ?", state, Long.valueOf(id));
      mvc.perform(get(path).header("Authorization", token)).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    }
    for (String state : new String[] {"ENDED", "ABANDONED"}) {
      jdbc.update("UPDATE learning_sessions SET status = ?, accumulated_active_ms = ?, "
          + "ended_at = ? WHERE id = ?", state, 123000L,
          java.sql.Timestamp.from(Instant.now()), Long.valueOf(id));
      mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk())
          .andExpect(jsonPath("$.data.sessionId").value(id))
          .andExpect(jsonPath("$.data.goalSnapshot").value("heartbeat goal"))
          .andExpect(jsonPath("$.data.status").value(state))
          .andExpect(jsonPath("$.data.accumulatedActiveMs").value(123000))
          .andExpect(jsonPath("$.data.extensionCount").value(0))
          .andExpect(jsonPath("$.data.extendedDurationMs").value(0))
          .andExpect(jsonPath("$.data.relatedDurationMs").doesNotExist());
    }
  }

  @Test
  void concurrentSummaryReadsAggregateActualExtensionsWithoutChangingSession() throws Exception {
    LoginResponse owner = desktop("summary-race-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    long numericId = Long.parseLong(id);
    jdbc.update("UPDATE learning_sessions SET status = 'ENDED', target_duration_ms = 1200000, "
        + "ended_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now()), numericId);
    for (long previous : new long[] {300000L, 600000L}) {
      long additional = previous;
      jdbc.update("INSERT INTO session_extensions (session_id, additional_duration_ms, "
          + "previous_target_duration_ms, target_duration_ms, created_at) VALUES (?, ?, ?, ?, ?)",
          numericId, additional, previous, previous + additional,
          java.sql.Timestamp.from(Instant.now()));
    }
    var before = sessions.findById(numericId).orElseThrow();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() ->
          mvc.perform(get("/api/v1/sessions/" + id + "/summary")
                  .header("Authorization", "Bearer " + owner.accessToken()))
              .andExpect(status().isOk()).andExpect(jsonPath("$.data.extensionCount").value(2))
              .andExpect(jsonPath("$.data.extendedDurationMs").value(900000))
              .andReturn().getResponse().getContentAsString())).toList();
      org.junit.jupiter.api.Assertions.assertEquals(json.readTree(results.getFirst()
          .get(10, TimeUnit.SECONDS)), json.readTree(results.getLast().get(10, TimeUnit.SECONDS)));
    }
    var after = sessions.findById(numericId).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(before.getVersion(), after.getVersion());
    org.junit.jupiter.api.Assertions.assertEquals(before.getStatus(), after.getStatus());
    org.junit.jupiter.api.Assertions.assertEquals(before.getEndedAt(), after.getEndedAt());
  }

  @Test
  void localEventValidatesHeadersAndReplaysWithoutPausingResumedSession() throws Exception {
    LoginResponse owner = desktop("events-owner-" + UUID.randomUUID());
    LoginResponse other = desktop("events-other-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    String path = "/api/v1/sessions/" + id + "/local-events";
    String token = "Bearer " + owner.accessToken();
    String body = "{\"localEventCode\":\"LOCAL_SCREEN_PERMISSION_REVOKED\","
        + "\"occurredAt\":\"2026-11-09T01:00:00.123456789Z\"}";
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(path).header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    mvc.perform(post(path).header("Authorization", token).header("X-Device-Id", "0")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(path).header("Authorization", "Bearer " + other.accessToken())
            .header("X-Device-Id", other.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
    for (String bad : new String[] {"{}", body.replace("LOCAL_SCREEN_PERMISSION_REVOKED",
        "LOCAL_EXTENSION_HOST_PERMISSION_MISSING"), body.replace("2026-11-09", "+999999-11-09")}) {
      mvc.perform(post(path).header("Authorization", token)
              .header("X-Device-Id", owner.device().deviceId())
              .contentType(MediaType.APPLICATION_JSON).content(bad))
          .andExpect(status().isBadRequest());
    }
    var before = sessions.findById(Long.valueOf(id)).orElseThrow();
    mvc.perform(post(path).header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTO_PAUSED"))
        .andExpect(jsonPath("$.data.pauseReason").value("REQUIRED_PERMISSION"))
        .andExpect(jsonPath("$.data.version").value(before.getVersion() + 1));
    mvc.perform(post("/api/v1/sessions/" + id + "/resume").header("Authorization", token)
            .header("Idempotency-Key", UUID.randomUUID())
            .header("If-Match-Version", before.getVersion() + 1)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"readiness\":{\"screenAnalysisReady\":true}}"))
        .andExpect(status().isOk());
    mvc.perform(post(path).header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));
    org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
        "SELECT count(*) FROM session_local_events WHERE session_id = ?", Integer.class,
        Long.valueOf(id)));
    org.junit.jupiter.api.Assertions.assertEquals(before.getAccumulatedActiveMs(),
        sessions.findById(Long.valueOf(id)).orElseThrow().getAccumulatedActiveMs());
  }

  private LinkedExtension linked(LoginResponse owner) {
    long familyId = jdbc.queryForObject("SELECT id FROM token_families WHERE device_id = ? "
        + "AND status = 'ACTIVE'", Long.class, Long.valueOf(owner.device().deviceId()));
    var principal = new FocusOnPrincipal(
        Long.parseLong(owner.user().userId()), Long.parseLong(owner.device().deviceId()), familyId,
        DeviceType.DESKTOP);
    return pairing.claim(new ClaimRequest(
        pairing.issue(principal).codeDisplay(), "event-ext-" + UUID.randomUUID()),
        "qa-" + UUID.randomUUID());
  }

  @Test
  void linkedExtensionPausesRunnerAndCannotReadLearningSummary() throws Exception {
    LoginResponse owner = desktop("event-extension-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    var extension = linked(owner);
    String path = "/api/v1/sessions/" + id;
    String body = "{\"localEventCode\":\"LOCAL_EXTENSION_HOST_PERMISSION_MISSING\","
        + "\"occurredAt\":\"2026-11-09T01:00:00Z\"}";
    mvc.perform(post(path + "/local-events")
            .header("Authorization", "Bearer " + extension.accessToken())
            .header("X-Device-Id", extension.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTO_PAUSED"))
        .andExpect(jsonPath("$.data.pauseReason").value("REQUIRED_PERMISSION"));
    mvc.perform(get(path + "/learning-summary")
            .header("Authorization", "Bearer " + extension.accessToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void learningSummaryReadsFiveActualStatesAndDoesNotInventMissingData() throws Exception {
    LoginResponse owner = desktop("learning-owner-" + UUID.randomUUID());
    LoginResponse other = desktop("learning-other-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    String path = "/api/v1/sessions/" + id + "/learning-summary";
    String token = "Bearer " + owner.accessToken();
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", token)).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(get(path).header("Authorization", "Bearer " + other.accessToken()))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    for (var state : LearningSummaryResponse.Status.values()) {
      var entity = BeanUtils.instantiateClass(
          LearningSummaryEntity.class);
      ReflectionTestUtils.setField(entity, "sessionId",
          Long.valueOf(id));
      ReflectionTestUtils.setField(entity, "status", state);
      boolean ready = state == LearningSummaryResponse.Status.READY;
      ReflectionTestUtils.setField(entity, "overview",
          ready ? "확인한 개념 정리" : null);
      ReflectionTestUtils.setField(entity, "nextStartPrompt",
          ready ? "개념을 다시 살펴보세요." : null);
      ReflectionTestUtils.setField(entity, "keyConcepts", ready
          ? java.util.List.of(new LearningSummaryResponse.KeyConcept(
              "트랜잭션", "원자적 변경", "관련 자료 열람")) : java.util.List.of());
      summaries.saveAndFlush(entity);
      var result = mvc.perform(get(path).header("Authorization", token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.learningSummaryStatus").value(state.name()))
          .andExpect(jsonPath("$.data.keyConcepts.length()").value(ready ? 1 : 0));
      if (ready) {
        // 저장된 READY 본문이 바뀌거나 빠지지 않고 그대로 반환되는지 확인한다.
        result.andExpect(jsonPath("$.data.overview").value("확인한 개념 정리"))
            .andExpect(jsonPath("$.data.nextStartPrompt").value("개념을 다시 살펴보세요."))
            .andExpect(jsonPath("$.data.keyConcepts[0].name").value("트랜잭션"))
            .andExpect(jsonPath("$.data.keyConcepts[0].briefExplanation").value("원자적 변경"))
            .andExpect(jsonPath("$.data.keyConcepts[0].sessionEvidence").value("관련 자료 열람"));
      }
    }
    org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
        "SELECT count(*) FROM learning_summaries WHERE session_id = ?", Integer.class,
        Long.valueOf(id)));
  }

  @Test
  void concurrentDuplicateLocalEventsCreateOneHistoryAndOneVersionChange() throws Exception {
    LoginResponse owner = desktop("event-race-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    long version = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("로컬 이벤트 동시 실행 대기 초과");
        }
        return mvc.perform(post("/api/v1/sessions/" + id + "/local-events")
                .header("Authorization", "Bearer " + owner.accessToken())
                .header("X-Device-Id", owner.device().deviceId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"localEventCode\":\"LOCAL_SCREEN_PERMISSION_REVOKED\","
                    + "\"occurredAt\":\"2026-11-09T01:00:00Z\"}"))
            .andExpect(status().isOk()).andReturn();
      })).toList();
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      for (var result : results) {
        result.get(10, TimeUnit.SECONDS);
      }
    }
    org.junit.jupiter.api.Assertions.assertEquals(version + 1,
        sessions.findById(Long.valueOf(id)).orElseThrow().getVersion());
    org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
        "SELECT count(*) FROM session_local_events WHERE session_id = ?", Integer.class,
        Long.valueOf(id)));
  }

  @Test
  void localEventsPreserveStoppedStatesAndRejectTerminalStates() throws Exception {
    LoginResponse owner = desktop("event-states-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    int offset = 0;
    for (String state : new String[] {"PAUSED", "AUTO_PAUSED", "AWAITING_END_CONFIRMATION",
        "ENDING", "ENDED", "ABANDONED"}) {
      jdbc.update("UPDATE learning_sessions SET status = ? WHERE id = ?", state, Long.valueOf(id));
      var before = sessions.findById(Long.valueOf(id)).orElseThrow();
      var response = mvc.perform(post("/api/v1/sessions/" + id + "/local-events")
              .header("Authorization", "Bearer " + owner.accessToken())
              .header("X-Device-Id", owner.device().deviceId())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"localEventCode\":\"LOCAL_SCREEN_PERMISSION_REVOKED\","
                  + "\"occurredAt\":\"2026-11-09T01:00:0" + offset++ + "Z\"}"))
          .andReturn().getResponse();
      boolean terminal = java.util.Set.of("ENDING", "ENDED", "ABANDONED").contains(state);
      org.junit.jupiter.api.Assertions.assertEquals(terminal ? 409 : 200, response.getStatus());
      var after = sessions.findById(Long.valueOf(id)).orElseThrow();
      org.junit.jupiter.api.Assertions.assertEquals(before.getVersion(), after.getVersion());
      org.junit.jupiter.api.Assertions.assertEquals(state, after.getStatus().name());
    }
  }

  @Test
  void anotherDesktopAndItsLinkedExtensionCannotPauseRunner() throws Exception {
    when(google.exchange(any())).thenReturn("same-user-event-" + UUID.randomUUID());
    LoginResponse owner = desktop("runner-" + UUID.randomUUID());
    LoginResponse second = desktop("other-desktop-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    var extension = linked(second);
    String path = "/api/v1/sessions/" + id + "/local-events";
    mvc.perform(post(path).header("Authorization", "Bearer " + second.accessToken())
            .header("X-Device-Id", second.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"localEventCode\":\"LOCAL_SCREEN_PERMISSION_REVOKED\","
                + "\"occurredAt\":\"2026-11-09T01:00:00Z\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(post(path).header("Authorization", "Bearer " + extension.accessToken())
            .header("X-Device-Id", extension.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"localEventCode\":\"LOCAL_EXTENSION_HOST_PERMISSION_MISSING\","
                + "\"occurredAt\":\"2026-11-09T01:00:00Z\"}"))
        .andExpect(status().isNotFound());
    org.junit.jupiter.api.Assertions.assertEquals("RUNNING",
        sessions.findById(Long.valueOf(id)).orElseThrow().getStatus().name());
  }

  @Test
  void concurrentHeartbeatAndPermissionEventCannotRevivePausedSession() throws Exception {
    LoginResponse owner = desktop("event-heartbeat-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    long version = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("권한 이벤트와 신호 동시 실행 대기 초과");
        }
        String command = index == 0 ? "local-events" : "heartbeats";
        String body = index == 0
            ? "{\"localEventCode\":\"LOCAL_SCREEN_PERMISSION_REVOKED\","
                + "\"occurredAt\":\"2026-11-09T01:00:00Z\"}"
            : heartbeatBody(Instant.now(), 1000);
        return mvc.perform(post("/api/v1/sessions/" + id + "/" + command)
                .header("Authorization", "Bearer " + owner.accessToken())
                .header("X-Device-Id", owner.device().deviceId())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
      })).toList();
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      for (var result : results) {
        result.get(10, TimeUnit.SECONDS);
      }
    }
    var stored = sessions.findById(Long.valueOf(id)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("AUTO_PAUSED", stored.getStatus().name());
    org.junit.jupiter.api.Assertions.assertEquals(version + 1, stored.getVersion());
    org.junit.jupiter.api.Assertions.assertTrue(java.util.Set.of(0L, 1000L)
        .contains(stored.getAccumulatedActiveMs()));
  }

  @Test
  void postgresSummarySchemaRejectsMalformedConceptsAndExcessiveText() throws Exception {
    try (var connection = dataSource.getConnection()) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()));
    }
    LoginResponse owner = desktop("learning-schema-" + UUID.randomUUID());
    long id = Long.parseLong(startForHeartbeat(owner));
    jdbc.update("INSERT INTO learning_summaries(session_id,summary_status,overview,"
        + "next_start_prompt,key_concepts) VALUES (?,'READY','학습 내용','이어 살펴보기',"
        + "CAST(? AS jsonb))", id, "[{\"name\":\"개념\",\"briefExplanation\":\"설명\"}]");
    for (String invalid : new String[] {"[{}]", "[null]", "{}",
        "[{\"name\":null,\"briefExplanation\":\"설명\"}]",
        "[{\"name\":\"개념\",\"briefExplanation\":\"설명\",\"contentPayload\":\"raw\"}]"}) {
      org.junit.jupiter.api.Assertions.assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
              "UPDATE learning_summaries SET key_concepts=CAST(? AS jsonb) WHERE session_id=?",
              invalid, id));
    }
    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
            "UPDATE learning_summaries SET overview=? WHERE session_id=?", "a".repeat(1200), id));
    // 개념 개수 경계: 짧은 유효 개념 5개는 허용하고 6개는 총 글자 수가 한도 안이어도 거절한다.
    org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.update(
        "UPDATE learning_summaries SET key_concepts=CAST(? AS jsonb) WHERE session_id=?",
        conceptsJson(5), id));
    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
            "UPDATE learning_summaries SET key_concepts=CAST(? AS jsonb) WHERE session_id=?",
            conceptsJson(6), id));
  }

  /** 총 1,200자보다 훨씬 짧은 유효 개념 count개의 JSON 배열을 만든다. */
  private static String conceptsJson(int count) {
    return java.util.stream.IntStream.rangeClosed(1, count)
        .mapToObj(index -> "{\"name\":\"개념" + index + "\",\"briefExplanation\":\"설명" + index
            + "\"}")
        .collect(java.util.stream.Collectors.joining(",", "[", "]"));
  }

  /** 만료된 세션은 이벤트로 정리 대상에서 빠져나갈 수 없다. */
  @Test
  void lateLocalEventCommitsAbandonedBeforeReturningConflict() throws Exception {
    LoginResponse owner = desktop("late-event-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    final long version = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
    jdbc.update("UPDATE learning_sessions SET last_resumed_at=?,last_heartbeat_at=NULL,"
        + "accumulated_active_ms=1234 WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(181)), Long.valueOf(id));
    mvc.perform(post("/api/v1/sessions/" + id + "/local-events")
            .header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(localEventBody(Instant.now())))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_ABANDONED"))
        .andExpect(jsonPath("$.details.endReason").value("HEARTBEAT_TIMEOUT"));
    var stored = sessions.findById(Long.valueOf(id)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("ABANDONED", stored.getStatus().name());
    org.junit.jupiter.api.Assertions.assertEquals(version + 1, stored.getVersion());
    org.junit.jupiter.api.Assertions.assertEquals(1234, stored.getAccumulatedActiveMs());
    org.junit.jupiter.api.Assertions.assertNotNull(stored.getEndedAt());
    org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
        "SELECT count(*) FROM session_local_events WHERE session_id=?", Integer.class,
        Long.valueOf(id)));
  }

  /** 이미 저장된 재전송은 세션이 만료된 뒤에도 상태를 바꾸지 않고 200으로 응답한다. */
  @Test
  void storedDuplicateAfterExpiryReturnsOkWithoutChangingSession() throws Exception {
    LoginResponse owner = desktop("dup-expired-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    long numericId = Long.parseLong(id);
    String path = "/api/v1/sessions/" + id + "/local-events";
    Instant occurred = Instant.now();
    mvc.perform(post(path).header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(localEventBody(occurred)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTO_PAUSED"));
    resumeForEventTest(owner, id);
    jdbc.update("UPDATE learning_sessions SET last_resumed_at=?,last_heartbeat_at=NULL WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(181)), numericId);
    long version = sessions.findById(numericId).orElseThrow().getVersion();
    mvc.perform(post(path).header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(localEventBody(occurred)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));
    var stored = sessions.findById(numericId).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("RUNNING", stored.getStatus().name());
    org.junit.jupiter.api.Assertions.assertEquals(version, stored.getVersion());
    org.junit.jupiter.api.Assertions.assertNull(stored.getEndedAt());
    org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
        "SELECT count(*) FROM session_local_events WHERE session_id=?", Integer.class, numericId));
    // 새 이벤트는 같은 만료 세션에서 여전히 ABANDONED 정리 뒤 409다.
    mvc.perform(post(path).header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(localEventBody(occurred.plusSeconds(5))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_ABANDONED"));
  }

  /** 상한 도달 오류가 필수 정지를 롤백하지 않고 이미 수신된 재전송은 그대로 허용된다. */
  @Test
  void localEventLimitPreservesPauseAndAcceptsStoredReplayAfterResume() throws Exception {
    LoginResponse owner = desktop("event-cap-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    long numericId = Long.parseLong(id);
    Instant occurred = Instant.now();
    for (int index = 0; index < 255; index++) {
      jdbc.update("INSERT INTO session_local_events(session_id,device_id,event_code,event_hash,"
          + "occurred_at,received_at) VALUES (?,?,'LOCAL_SCREEN_PERMISSION_REVOKED',?,?,?)",
          numericId, Long.valueOf(owner.device().deviceId()), String.format("%064x", index),
          java.sql.Timestamp.from(occurred), java.sql.Timestamp.from(occurred));
    }
    String path = "/api/v1/sessions/" + id + "/local-events";
    mvc.perform(post(path).header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(localEventBody(occurred))).andExpect(status().isOk());
    resumeForEventTest(owner, id);
    mvc.perform(post(path).header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(localEventBody(occurred.plusSeconds(1))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_LOCAL_EVENT_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.retryable").value(false))
        .andExpect(jsonPath("$.details.currentStatus").value("AUTO_PAUSED"));
    org.junit.jupiter.api.Assertions.assertEquals("AUTO_PAUSED",
        sessions.findById(numericId).orElseThrow().getStatus().name());
    resumeForEventTest(owner, id);
    mvc.perform(post(path).header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(localEventBody(occurred)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));
    org.junit.jupiter.api.Assertions.assertEquals(256, jdbc.queryForObject(
        "SELECT count(*) FROM session_local_events WHERE session_id=?", Integer.class, numericId));
  }

  private void resumeForEventTest(LoginResponse owner, String id) throws Exception {
    long version = sessions.findById(Long.valueOf(id)).orElseThrow().getVersion();
    mvc.perform(post("/api/v1/sessions/" + id + "/resume")
            .header("Authorization", "Bearer " + owner.accessToken())
            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match-Version", version)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"readiness\":{\"screenAnalysisReady\":true}}"))
        .andExpect(status().isOk());
  }

  private String localEventBody(Instant occurred) {
    return "{\"localEventCode\":\"LOCAL_SCREEN_PERMISSION_REVOKED\",\"occurredAt\":\""
        + occurred + "\"}";
  }

  /** 이전 구간의 신호가 오래돼도 방금 재개한 세션을 만료시키지 않는다. */
  @Test
  void localEventUsesLatestResumeOrHeartbeatForDeadline() throws Exception {
    LoginResponse owner = desktop("fresh-event-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    for (boolean resumed : new boolean[] {true, false}) {
      Instant now = Instant.now();
      jdbc.update("UPDATE learning_sessions SET status='RUNNING',last_resumed_at=?,"
          + "last_heartbeat_at=? WHERE id=?",
          java.sql.Timestamp.from(resumed ? now : now.minusSeconds(600)),
          java.sql.Timestamp.from(resumed ? now.minusSeconds(600) : now), Long.valueOf(id));
      mvc.perform(post("/api/v1/sessions/" + id + "/local-events")
              .header("Authorization", "Bearer " + owner.accessToken())
              .header("X-Device-Id", owner.device().deviceId())
              .contentType(MediaType.APPLICATION_JSON).content(localEventBody(now)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTO_PAUSED"));
    }
  }

  /** 마지막 저장 슬롯 경쟁에서도 상한을 넘기지 않고 모든 권한 유실 정지를 유지한다. */
  @Test
  void concurrentDistinctEventsCannotExceedSessionLimit() throws Exception {
    LoginResponse owner = desktop("event-cap-race-" + UUID.randomUUID());
    String id = startForHeartbeat(owner);
    long numericId = Long.parseLong(id);
    for (int index = 0; index < 255; index++) {
      jdbc.update("INSERT INTO session_local_events(session_id,device_id,event_code,event_hash,"
          + "occurred_at,received_at) VALUES (?,?,'LOCAL_SCREEN_PERMISSION_REVOKED',?,?,?)",
          numericId, Long.valueOf(owner.device().deviceId()), String.format("%064x", index),
          java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
    }
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("이벤트 저장 한도 경쟁 대기 초과");
        }
        return mvc.perform(post("/api/v1/sessions/" + id + "/local-events")
                .header("Authorization", "Bearer " + owner.accessToken())
                .header("X-Device-Id", owner.device().deviceId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(localEventBody(Instant.now().plusSeconds(index))))
            .andReturn().getResponse().getStatus();
      })).toList();
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      var codes = new java.util.ArrayList<Integer>();
      for (var result : results) {
        codes.add(result.get(10, TimeUnit.SECONDS));
      }
      java.util.Collections.sort(codes);
      org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(200, 409), codes);
    }
    org.junit.jupiter.api.Assertions.assertEquals(256, jdbc.queryForObject(
        "SELECT count(*) FROM session_local_events WHERE session_id=?", Integer.class, numericId));
    org.junit.jupiter.api.Assertions.assertEquals("AUTO_PAUSED",
        sessions.findById(numericId).orElseThrow().getStatus().name());
  }

  private LoginResponse desktop(String installationId) {
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), installationId,
        "http://127.0.0.1:49152", new DesktopDevice(installationId, "QA", "MACOS")));
  }
}
