package com.capstone.backend.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserEntity.OnboardingStatus;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.goal.persistence.GoalRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.user.service.OnboardingReadinessService;
import com.capstone.backend.user.service.OnboardingReadinessService.Readiness;
import com.capstone.backend.user.service.OnboardingReadinessService.Steps;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** 목표 수정·삭제의 인증과 이력 보존을 검증한다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class GoalUpdateDeleteApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private UserDeviceRepository devices;
  @Autowired private GoalRepository goals;
  @Autowired private LearningSessionRepository sessions;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private OnboardingReadinessService onboarding;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "goal-edit-" + UUID.randomUUID());
    when(onboarding.get(any(), isNull())).thenReturn(new Readiness(OnboardingStatus.COMPLETED,
        "COMPLETED", new Steps(true, true, true, true, true)));
  }

  @Test
  void concurrentSessionStartAndDeleteCannotBothSucceed() throws Exception {
    assertConcurrentStartAndChange(false);
  }

  @Test
  void concurrentSessionStartAndUpdateKeepCorrectSnapshot() throws Exception {
    assertConcurrentStartAndChange(true);
  }

  @Test
  void postgresSessionStartAndDeleteWaitForUserLock() throws Exception {
    assertRequestsWaitForUserLock(false);
  }

  @Test
  void postgresSessionStartAndUpdateWaitForUserLock() throws Exception {
    assertRequestsWaitForUserLock(true);
  }

  @Test
  void postgresInvalidUpdateDoesNotWaitForUserLock() throws Exception {
    try (Connection blocker = jdbc.getDataSource().getConnection()) {
      assumeTrue("PostgreSQL".equals(blocker.getMetaData().getDatabaseProductName()));
      LoginResponse owner = desktop("invalid-lock-" + UUID.randomUUID());
      String id = createGoal(owner);
      blocker.setAutoCommit(false);
      try (var statement = blocker.prepareStatement(
          "SELECT id FROM users WHERE id = ? FOR UPDATE")) {
        statement.setLong(1, Long.parseLong(owner.user().userId()));
        try (var result = statement.executeQuery()) {
          assertTrue(result.next());
        }
      }
      try (var pool = Executors.newSingleThreadExecutor()) {
        var response = pool.submit(() -> mvc.perform(patch("/api/v1/goals/" + id)
            .header("Authorization", token(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"a\"}")).andReturn().getResponse().getStatus());
        try {
          assertEquals(400, response.get(3, TimeUnit.SECONDS));
        } finally {
          blocker.rollback();
        }
      }
    }
  }

  private void assertRequestsWaitForUserLock(boolean update) throws Exception {
    try (Connection blocker = jdbc.getDataSource().getConnection()) {
      assumeTrue("PostgreSQL".equals(blocker.getMetaData().getDatabaseProductName()));
      LoginResponse owner = desktop("lock-race-" + UUID.randomUUID());
      String id = createGoal(owner);
      String body = "{\"goalId\":\"" + id + "\",\"targetDurationMs\":1800000,"
          + "\"timeZone\":\"Asia/Seoul\",\"sessionOptions\":{\"interventionLevel\":\"NORMAL\","
          + "\"notificationChannels\":[\"MOLE_UI\"],\"cameraAnalysisEnabled\":false},"
          + "\"startReadiness\":{\"screenAnalysisReady\":true}}";
      blocker.setAutoCommit(false);
      int blockerPid;
      try (var statement = blocker.prepareStatement("SELECT pg_backend_pid()")) {
        try (var result = statement.executeQuery()) {
          result.next();
          blockerPid = result.getInt(1);
        }
      }
      try (var statement = blocker.prepareStatement(
          "SELECT id FROM users WHERE id = ? FOR UPDATE")) {
        statement.setLong(1, Long.parseLong(owner.user().userId()));
        try (var result = statement.executeQuery()) {
          assertTrue(result.next());
        }
      }
      try (var pool = Executors.newFixedThreadPool(2)) {
        var start = pool.submit(() -> mvc.perform(post("/api/v1/sessions")
            .header("Authorization", token(owner)).header("X-Device-Id", owner.device().deviceId())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse()
            .getStatus());
        var change = pool.submit(() -> {
          if (update) {
            return mvc.perform(patch("/api/v1/goals/" + id).header("Authorization", token(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"goalText\":\"변경 목표\"}"))
                .andReturn().getResponse().getStatus();
          }
          return mvc.perform(delete("/api/v1/goals/" + id).header("Authorization", token(owner)))
              .andReturn().getResponse().getStatus();
        });
        try {
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
          int waiting = 0;
          while (System.nanoTime() < deadline && waiting < 2) {
            waiting = jdbc.queryForObject("WITH RECURSIVE waiters(pid) AS ("
                + "SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)) "
                + "UNION SELECT activity.pid FROM pg_stat_activity activity "
                + "JOIN waiters ON waiters.pid = ANY(pg_blocking_pids(activity.pid))) "
                + "SELECT count(*) FROM waiters", Integer.class, blockerPid);
            if (waiting < 2) {
              Thread.sleep(25);
            }
          }
          assertEquals(2, waiting, "두 요청이 실제 사용자 행 잠금을 기다려야 한다");
        } finally {
          blocker.rollback();
        }
        int started = start.get(20, TimeUnit.SECONDS);
        int changed = change.get(20, TimeUnit.SECONDS);
        if (update) {
          assertEquals(201, started);
          assertTrue(changed == 200 || changed == 409);
          var active = sessions.findByUserIdAndStatusIn(Long.valueOf(owner.user().userId()),
              com.capstone.backend.session.persistence.SessionStatus.activeStatuses())
              .orElseThrow();
          assertEquals(changed == 200 ? "변경 목표" : "기존 목표", active.getGoalTextSnapshot());
        } else {
          assertTrue((started == 201 && changed == 409) || (started == 404 && changed == 204),
              "start=" + started + ", delete=" + changed);
        }
      }
    }
  }

  private void assertConcurrentStartAndChange(boolean update) throws Exception {
    LoginResponse owner = desktop("edit-race-" + UUID.randomUUID());
    String id = createGoal(owner);
    String body = "{\"goalId\":\"" + id + "\",\"targetDurationMs\":1800000,"
        + "\"timeZone\":\"Asia/Seoul\",\"sessionOptions\":{\"interventionLevel\":\"NORMAL\","
        + "\"notificationChannels\":[\"MOLE_UI\"],\"cameraAnalysisEnabled\":false},"
        + "\"startReadiness\":{\"screenAnalysisReady\":true}}";
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var create = pool.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("시작 요청 대기 시간 초과");
        }
        return mvc.perform(post("/api/v1/sessions").header("Authorization", token(owner))
                .header("X-Device-Id", owner.device().deviceId())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().getResponse().getStatus();
      });
      var change = pool.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("삭제 요청 대기 시간 초과");
        }
        if (update) {
          return mvc.perform(patch("/api/v1/goals/" + id).header("Authorization", token(owner))
                  .contentType(MediaType.APPLICATION_JSON).content("{\"goalText\":\"변경 목표\"}"))
              .andReturn().getResponse().getStatus();
        }
        return mvc.perform(delete("/api/v1/goals/" + id).header("Authorization", token(owner)))
            .andReturn().getResponse().getStatus();
      });
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      int created = create.get(20, TimeUnit.SECONDS);
      int changed = change.get(20, TimeUnit.SECONDS);
      if (update) {
        org.junit.jupiter.api.Assertions.assertEquals(201, created);
        org.junit.jupiter.api.Assertions.assertTrue(changed == 200 || changed == 409);
        var active = sessions.findByUserIdAndStatusIn(Long.valueOf(owner.user().userId()),
            com.capstone.backend.session.persistence.SessionStatus.activeStatuses()).orElseThrow();
        assertEquals(changed == 200 ? "변경 목표" : "기존 목표", active.getGoalTextSnapshot());
      } else {
        org.junit.jupiter.api.Assertions.assertTrue((created == 201 && changed == 409)
            || (created == 404 && changed == 204), "start=" + created + ", delete=" + changed);
      }
    }
  }

  @Test
  void updatesAndSoftDeletesOnlyOwnersGoals() throws Exception {
    LoginResponse owner = desktop("edit-owner");
    LoginResponse other = desktop("edit-other");
    String id = createGoal(owner);
    String path = "/api/v1/goals/" + id;
    String body = "{\"goalText\":\"수정한 목표\"}";
    mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete(path)).andExpect(status().isUnauthorized());
    mvc.perform(patch(path).header("Authorization", token(other))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
    mvc.perform(patch(path).header("Authorization", token(other))
            .contentType(MediaType.APPLICATION_JSON).content("{\"goalText\":\"a\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(patch("/api/v1/goals/not-an-id").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"goalText\":\"a\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(delete(path).header("Authorization", token(other)))
        .andExpect(status().isNotFound());
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"goalText\":\"a\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("GOAL_TEXT_INVALID"));
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.goalText").value("수정한 목표"));
    mvc.perform(delete(path).header("Authorization", token(owner)))
        .andExpect(status().isNoContent()).andExpect(result ->
            assertEquals("", result.getResponse().getContentAsString()));
    assertNotNull(goals.findById(Long.valueOf(id)).orElseThrow().getDeletedAt());
    mvc.perform(get(path).header("Authorization", token(owner))).andExpect(status().isNotFound());
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
    mvc.perform(delete(path).header("Authorization", token(owner)))
        .andExpect(status().isNotFound());
  }

  @Test
  void activeSessionBlocksChangesAndEndedSnapshotRemains() throws Exception {
    LoginResponse owner = desktop("edit-session");
    String id = createGoal(owner);
    var user = users.findById(Long.valueOf(owner.user().userId())).orElseThrow();
    var device = devices.findById(Long.valueOf(owner.device().deviceId())).orElseThrow();
    var goal = goals.findById(Long.valueOf(id)).orElseThrow();
    Instant now = Instant.now();
    final var session = sessions.saveAndFlush(LearningSessionEntity.create(user, goal, device,
        1800000, "Asia/Seoul", now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), "{}", "v1", now));
    String path = "/api/v1/goals/" + id;
    String body = "{\"goalText\":\"새 목표\"}";
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GOAL_UPDATE_BLOCKED"));
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"goalText\":\"a\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GOAL_UPDATE_BLOCKED"));
    mvc.perform(delete(path).header("Authorization", token(owner)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GOAL_UPDATE_BLOCKED"));
    jdbc.update("UPDATE learning_sessions SET status = 'ENDED' WHERE id = ?", session.getId());
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    mvc.perform(delete(path).header("Authorization", token(owner)))
        .andExpect(status().isNoContent());
    assertEquals("기존 목표", sessions.findById(session.getId()).orElseThrow().getGoalTextSnapshot());
  }

  private String createGoal(LoginResponse owner) throws Exception {
    String response = mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"기존 목표\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(response).path("data").path("goalId").asText();
  }

  private String token(LoginResponse owner) {
    return "Bearer " + owner.accessToken();
  }

  private LoginResponse desktop(String installationId) {
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), installationId,
        "http://127.0.0.1:49152", new DesktopDevice(installationId, "QA", "MACOS")));
  }
}
