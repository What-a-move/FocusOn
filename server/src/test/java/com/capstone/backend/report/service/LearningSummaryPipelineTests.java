package com.capstone.backend.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.capstone.backend.report.service.LearningSummaryAiClient.LearningSummaryAiException;
import com.capstone.backend.user.service.OnboardingReadinessService;
import com.capstone.backend.user.service.OnboardingReadinessService.Readiness;
import com.capstone.backend.user.service.OnboardingReadinessService.Steps;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 종료 → QUEUED → GENERATING → READY/INSUFFICIENT_DATA/FAILED 전이와 동시 Worker 중복 방지를
 * 실제 종료 API와 DB로 확인한다. H2와 PostgreSQL(CI) 모두에서 실행한다.
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@ExtendWith(OutputCaptureExtension.class)
class LearningSummaryPipelineTests {
  private static final String READY_OUTPUT = """
      {"overview":"트랜잭션 격리 수준 자료를 두 번 확인했습니다.",
       "keyConcepts":[{"name":"격리 수준","briefExplanation":"동시 트랜잭션의 가시성 규칙",
         "sessionEvidence":"관련 문서 열람"}],
       "nextStartPrompt":"격리 수준 예시를 다시 살펴보세요."}
      """;
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LearningSummaryGenerator generator;
  @Autowired private LearningSummaryWorker worker;
  @MockitoBean private LearningSummaryAiClient ai;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private OnboardingReadinessService onboarding;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "summary-" + UUID.randomUUID());
    when(onboarding.get(any(), any())).thenReturn(new Readiness(OnboardingStatus.COMPLETED,
        "COMPLETED", new Steps(true, true, true, true, true)));
    // 다른 테스트가 남긴 대기 행이 이 테스트의 Worker 실행에 섞이지 않도록 정리한다.
    jdbc.update("UPDATE learning_summaries SET summary_status = 'FAILED', "
        + "next_attempt_at = NULL, lease_expires_at = NULL "
        + "WHERE summary_status IN ('QUEUED', 'GENERATING')");
    reset(ai);
  }

  @Test
  void userEndCreatesQueuedSummaryImmediatelyWithThirtySecondInputClose() throws Exception {
    var session = endedSession();
    var row = row(session.id());
    assertEquals("QUEUED", row.get("summary_status"));
    Instant endedAt = instant(jdbc.queryForMap(
        "SELECT ended_at FROM learning_sessions WHERE id = ?", session.id()).get("ended_at"));
    assertEquals(endedAt.plusSeconds(30), instant(row.get("input_closes_at")));
    assertEquals(endedAt.plusSeconds(30), instant(row.get("next_attempt_at")));
    // 종료 직후 Desktop 조회가 404가 아니라 생성 대기 상태를 받는다.
    mvc.perform(get("/api/v1/sessions/" + session.id() + "/learning-summary")
            .header("Authorization", session.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.learningSummaryStatus").value("QUEUED"))
        .andExpect(jsonPath("$.data.overview").doesNotExist());
    // 입력 마감 전에는 Worker가 가져가지 않는다.
    worker.run();
    assertEquals("QUEUED", row(session.id()).get("summary_status"));
    verify(ai, never()).generate(any(), anyString());
  }

  @Test
  void fewerThanTwoDistinctEvidencesEndsInsufficientWithoutModelCall() throws Exception {
    var session = endedSession();
    evidence(session.id(), "격리 수준", Instant.now());
    // 같은 개념은 해시로 한 번만 저장되므로 서로 다른 근거 수는 1개다.
    assertFalse(tryDuplicateEvidence(session.id(), "격리 수준"));
    due(session.id());
    worker.run();
    var row = row(session.id());
    assertEquals("INSUFFICIENT_DATA", row.get("summary_status"));
    assertEquals(1, ((Number) row.get("attempt_count")).intValue());
    assertNull(row.get("overview"));
    verify(ai, never()).generate(any(), anyString());
    mvc.perform(get("/api/v1/sessions/" + session.id() + "/learning-summary")
            .header("Authorization", session.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.learningSummaryStatus").value("INSUFFICIENT_DATA"))
        .andExpect(jsonPath("$.data.keyConcepts.length()").value(0));
  }

  @Test
  void evidenceReceivedAfterInputCloseIsNotUsed() throws Exception {
    var session = endedSession();
    Instant closes = instant(row(session.id()).get("input_closes_at"));
    evidence(session.id(), "격리 수준", closes.minusSeconds(1));
    evidence(session.id(), "MVCC", closes.plusSeconds(1));
    due(session.id());
    worker.run();
    assertEquals("INSUFFICIENT_DATA", row(session.id()).get("summary_status"));
    verify(ai, never()).generate(any(), anyString());
  }

  @Test
  void enoughEvidenceCallsModelOnceWithFixedInputAndStoresReady() throws Exception {
    var session = endedSession();
    evidence(session.id(), "격리 수준", Instant.now());
    evidence(session.id(), "MVCC", Instant.now());
    when(ai.generate(any(), anyString())).thenReturn(json.readTree(READY_OUTPUT));
    due(session.id());
    worker.run();
    verify(ai, times(1)).generate(argThat(input -> input.sessionId() == session.id()
        && input.goalSnapshot().equals("정리 검증") && input.evidences().size() == 2
        && input.evidences().stream().allMatch(evidence -> evidence.concept().length() <= 120)),
        anyString());
    assertEquals("READY", row(session.id()).get("summary_status"));
    mvc.perform(get("/api/v1/sessions/" + session.id() + "/learning-summary")
            .header("Authorization", session.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.learningSummaryStatus").value("READY"))
        .andExpect(jsonPath("$.data.overview").value("트랜잭션 격리 수준 자료를 두 번 확인했습니다."))
        .andExpect(jsonPath("$.data.keyConcepts[0].name").value("격리 수준"))
        .andExpect(jsonPath("$.data.nextStartPrompt").value("격리 수준 예시를 다시 살펴보세요."));
  }

  @Test
  void transientFailuresRetryAfterOneAndFiveMinutesThenFailWithoutTouchingSession()
      throws Exception {
    var session = endedSession();
    evidence(session.id(), "격리 수준", Instant.now());
    evidence(session.id(), "MVCC", Instant.now());
    when(ai.generate(any(), anyString())).thenThrow(new LearningSummaryAiException(true));
    final var sessionBefore = sessionRow(session.id());
    List<Duration> expectedDelays = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));
    for (int attempt = 1; attempt <= 2; attempt++) {
      due(session.id());
      final Instant before = Instant.now();
      worker.run();
      var row = row(session.id());
      assertEquals("QUEUED", row.get("summary_status"));
      assertEquals(attempt, ((Number) row.get("attempt_count")).intValue());
      Duration delay = Duration.between(before, instant(row.get("next_attempt_at")));
      Duration expected = expectedDelays.get(attempt - 1);
      assertTrue(delay.compareTo(expected) >= 0 && delay.compareTo(expected.plusSeconds(5)) < 0,
          "재시도 간격이 " + expected + "이어야 합니다: " + delay);
      // 실행 시각 전에는 다시 가져가지 않는다.
      worker.run();
      assertEquals(attempt, ((Number) row(session.id()).get("attempt_count")).intValue());
    }
    due(session.id());
    worker.run();
    var row = row(session.id());
    assertEquals("FAILED", row.get("summary_status"));
    assertEquals(3, ((Number) row.get("attempt_count")).intValue());
    assertNull(row.get("next_attempt_at"));
    verify(ai, times(3)).generate(any(), anyString());
    assertEquals(sessionBefore, sessionRow(session.id()));
  }

  @Test
  void permanentFailureFailsImmediately() throws Exception {
    var session = endedSession();
    evidence(session.id(), "격리 수준", Instant.now());
    evidence(session.id(), "MVCC", Instant.now());
    when(ai.generate(any(), anyString())).thenThrow(new LearningSummaryAiException(false));
    due(session.id());
    worker.run();
    assertEquals("FAILED", row(session.id()).get("summary_status"));
    verify(ai, times(1)).generate(any(), anyString());
  }

  @Test
  void sensitiveOrInvalidOutputIsDiscardedAndNeverStoredOrLogged(CapturedOutput output)
      throws Exception {
    var session = endedSession();
    evidence(session.id(), "격리 수준", Instant.now());
    evidence(session.id(), "MVCC", Instant.now());
    when(ai.generate(any(), anyString())).thenReturn(json.readTree("""
        {"overview":"https://leak.example.com/private?q=secret 문서를 봤습니다.",
         "keyConcepts":[{"name":"격리 수준","briefExplanation":"qa.user@example.com"}],
         "nextStartPrompt":"다시 보세요."}
        """));
    due(session.id());
    worker.run();
    var row = row(session.id());
    assertEquals("FAILED", row.get("summary_status"));
    assertNull(row.get("overview"));
    assertNull(row.get("next_start_prompt"));
    assertFalse(String.valueOf(row.get("key_concepts")).contains("example"));
    assertFalse(output.getAll().contains("leak.example.com"));
    assertFalse(output.getAll().contains("qa.user@example.com"));
    assertFalse(output.getAll().contains("격리 수준"), "근거 개념도 로그에 남기지 않는다");
    verify(ai, times(1)).generate(any(), anyString());
  }

  @Test
  void concurrentWorkersGenerateOnlyOnce() throws Exception {
    var session = endedSession();
    evidence(session.id(), "격리 수준", Instant.now());
    evidence(session.id(), "MVCC", Instant.now());
    AtomicInteger calls = new AtomicInteger();
    when(ai.generate(any(), anyString())).thenAnswer(invocation -> {
      calls.incrementAndGet();
      Thread.sleep(200);
      return json.readTree(READY_OUTPUT);
    });
    due(session.id());
    int workers = 8;
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger claimed = new AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(workers)) {
      var results = java.util.stream.IntStream.range(0, workers).mapToObj(index ->
          executor.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
              throw new IllegalStateException("동시 실행 대기 초과");
            }
            if (generator.process(session.id())) {
              claimed.incrementAndGet();
            }
            return null;
          })).toList();
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      for (var result : results) {
        result.get(30, TimeUnit.SECONDS);
      }
    }
    assertEquals(1, claimed.get());
    assertEquals(1, calls.get());
    var row = row(session.id());
    assertEquals("READY", row.get("summary_status"));
    assertEquals(1, ((Number) row.get("attempt_count")).intValue());
    assertEquals(1, jdbc.queryForObject(
        "SELECT count(*) FROM learning_summaries WHERE session_id = ?", Integer.class,
        session.id()));
  }

  @Test
  void lateResultFromExpiredLeaseCannotOverwriteNewAttempt() throws Exception {
    var session = endedSession();
    evidence(session.id(), "격리 수준", Instant.now());
    evidence(session.id(), "MVCC", Instant.now());
    when(ai.generate(any(), anyString())).thenAnswer(invocation -> {
      // 첫 Worker가 응답을 기다리는 동안 lease가 끝나 다른 Worker가 두 번째 시도를 점유한 상황이다.
      jdbc.update("UPDATE learning_summaries SET lease_expires_at = ? WHERE session_id = ?",
          Timestamp.from(Instant.now().minusSeconds(1)), session.id());
      assertEquals(1, jdbc.update("UPDATE learning_summaries SET attempt_count = attempt_count + 1,"
          + " lease_expires_at = ? WHERE session_id = ? AND summary_status = 'GENERATING'",
          Timestamp.from(Instant.now().plusSeconds(60)), session.id()));
      return json.readTree(READY_OUTPUT);
    });
    due(session.id());
    assertTrue(generator.process(session.id()));
    var row = row(session.id());
    assertEquals("GENERATING", row.get("summary_status"));
    assertEquals(2, ((Number) row.get("attempt_count")).intValue());
    assertNull(row.get("overview"));
  }

  @Test
  void expiredLeaseIsReclaimedAndExhaustedLeaseFails() throws Exception {
    var retry = endedSession();
    var exhausted = endedSession();
    Timestamp past = Timestamp.from(Instant.now().minusSeconds(1));
    jdbc.update("UPDATE learning_summaries SET summary_status = 'GENERATING', attempt_count = 1, "
        + "next_attempt_at = NULL, lease_expires_at = ? WHERE session_id = ?", past, retry.id());
    jdbc.update("UPDATE learning_summaries SET summary_status = 'GENERATING', attempt_count = 3, "
        + "next_attempt_at = NULL, lease_expires_at = ? WHERE session_id = ?", past,
        exhausted.id());
    worker.run();
    var reclaimed = row(retry.id());
    assertEquals("INSUFFICIENT_DATA", reclaimed.get("summary_status"));
    assertEquals(2, ((Number) reclaimed.get("attempt_count")).intValue());
    assertEquals("FAILED", row(exhausted.id()).get("summary_status"));
  }

  @Test
  void workerRecoversMissingQueueForRecentUserEndOnly() throws Exception {
    var ended = endedSession();
    var abandoned = endedSession();
    jdbc.update("DELETE FROM learning_summaries WHERE session_id IN (?, ?)", ended.id(),
        abandoned.id());
    Timestamp twoMinutesAgo = Timestamp.from(Instant.now().minus(Duration.ofMinutes(2)));
    jdbc.update("UPDATE learning_sessions SET ended_at = ? WHERE id = ?", twoMinutesAgo,
        ended.id());
    jdbc.update("UPDATE learning_sessions SET status = 'ABANDONED', "
        + "end_reason = 'HEARTBEAT_TIMEOUT', ended_at = ? WHERE id = ?", twoMinutesAgo,
        abandoned.id());
    worker.run();
    // 입력 마감(종료 + 30초)이 이미 지나 같은 실행에서 근거 부족으로 끝난다.
    assertEquals("INSUFFICIENT_DATA", row(ended.id()).get("summary_status"));
    assertEquals(0, jdbc.queryForObject(
        "SELECT count(*) FROM learning_summaries WHERE session_id = ?", Integer.class,
        abandoned.id()));
  }

  private Ended endedSession() throws Exception {
    var owner = desktop("summary-" + UUID.randomUUID());
    String token = "Bearer " + owner.accessToken();
    String goal = mvc.perform(post("/api/v1/goals").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"정리 검증\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String goalId = json.readTree(goal).path("data").path("goalId").asString();
    String started = mvc.perform(post("/api/v1/sessions").header("Authorization", token)
            .header("X-Device-Id", owner.device().deviceId())
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON).content(sessionRequest(goalId)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    JsonNode data = json.readTree(started).path("data");
    long id = Long.parseLong(data.path("sessionId").asString());
    jdbc.update("UPDATE learning_sessions SET status = 'PAUSED', accumulated_active_ms = 1234 "
        + "WHERE id = ?", id);
    mvc.perform(post("/api/v1/sessions/" + id + "/end").header("Authorization", token)
            .header("Idempotency-Key", UUID.randomUUID())
            .header("If-Match-Version", data.path("version").asLong())
            .contentType(MediaType.APPLICATION_JSON).content("{\"accumulatedActiveMs\":1234}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"));
    return new Ended(id, token);
  }

  private void evidence(long sessionId, String concept, Instant receivedAt) {
    long runId = analysisRun(sessionId);
    jdbc.update("INSERT INTO learning_review_evidences(session_id, analysis_run_id, concept, "
        + "evidence_hash, observed_at, relation_confidence, created_at) "
        + "VALUES (?, ?, ?, ?, ?, 0.9, ?)", sessionId, runId, concept, hash(concept),
        Timestamp.from(receivedAt), Timestamp.from(receivedAt));
  }

  private boolean tryDuplicateEvidence(long sessionId, String concept) {
    try {
      evidence(sessionId, concept, Instant.now());
      return true;
    } catch (org.springframework.dao.DataIntegrityViolationException exception) {
      return false;
    }
  }

  private long analysisRun(long sessionId) {
    long deviceId = jdbc.queryForObject(
        "SELECT runner_device_id FROM learning_sessions WHERE id = ?", Long.class, sessionId);
    Timestamp now = Timestamp.from(Instant.now());
    String clientRunId = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO analysis_runs(session_id, device_id, client_run_id, content_source, "
        + "observed_at, received_at, sequence, relation, relation_confidence, analysis_status, "
        + "policy_version, created_at, current_eligible, ordering_untrusted) "
        + "VALUES (?, ?, CAST(? AS UUID), 'CHROME_DOM', ?, ?, 1, 'RELATED', 0.9, 'COMPLETED', "
        + "'v1', ?, TRUE, FALSE)", sessionId, deviceId, clientRunId, now, now, now);
    return jdbc.queryForObject("SELECT id FROM analysis_runs WHERE client_run_id = CAST(? AS UUID)",
        Long.class, clientRunId);
  }

  /** 테스트에서 30초 입력 마감을 기다리지 않도록 실행 시각을 과거로 당긴다. */
  private void due(long sessionId) {
    jdbc.update("UPDATE learning_summaries SET next_attempt_at = ? WHERE session_id = ? "
        + "AND summary_status = 'QUEUED'", Timestamp.from(Instant.now().minusSeconds(1)),
        sessionId);
  }

  private Map<String, Object> row(long sessionId) {
    return jdbc.queryForMap("SELECT * FROM learning_summaries WHERE session_id = ?", sessionId);
  }

  private Map<String, Object> sessionRow(long sessionId) {
    return jdbc.queryForMap("SELECT status, version, accumulated_active_ms, ended_at, end_reason "
        + "FROM learning_sessions WHERE id = ?", sessionId);
  }

  private static Instant instant(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant();
    }
    if (value instanceof java.time.OffsetDateTime offset) {
      return offset.toInstant();
    }
    throw new IllegalStateException("시각 컬럼 타입을 확인하세요: " + value);
  }

  private static String hash(String concept) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(concept.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String sessionRequest(String goalId) {
    return "{\"goalId\":\"" + goalId + "\",\"targetDurationMs\":1800000,"
        + "\"timeZone\":\"Asia/Seoul\",\"sessionOptions\":{\"interventionLevel\":\"GENTLE\","
        + "\"notificationChannels\":[\"MOLE_UI\"],\"cameraAnalysisEnabled\":false},"
        + "\"startReadiness\":{\"screenAnalysisReady\":true}}";
  }

  private LoginResponse desktop(String installationId) {
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), installationId,
        "http://127.0.0.1:49152", new DesktopDevice(installationId, "QA", "MACOS")));
  }

  private record Ended(long id, String token) {}
}
