package com.capstone.backend.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.persistence.AnalysisRunEntity;
import com.capstone.backend.analysis.persistence.AnalysisRunRepository;
import com.capstone.backend.analysis.service.AnalysisDecision;
import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.common.api.RequestIdFilter;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.goal.persistence.GoalRepository;
import com.capstone.backend.intervention.dto.InterventionDtos.Acknowledge;
import com.capstone.backend.intervention.dto.InterventionDtos.Action;
import com.capstone.backend.intervention.dto.InterventionDtos.Status;
import com.capstone.backend.intervention.dto.InterventionDtos.Type;
import com.capstone.backend.intervention.persistence.InterventionDeliveryRepository;
import com.capstone.backend.intervention.persistence.InterventionEventEntity;
import com.capstone.backend.intervention.persistence.InterventionEventRepository;
import com.capstone.backend.intervention.persistence.InterventionPolicyRepository;
import com.capstone.backend.intervention.service.ForegroundPolicyContext;
import com.capstone.backend.intervention.service.HeartbeatPolicyEvaluator;
import com.capstone.backend.intervention.service.InterventionPolicyService;
import com.capstone.backend.intervention.service.InterventionService;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.user.persistence.UserConsentEntity;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserConsentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** 실제 저장과 인증 HTTP를 검증하며 전경 입력은 명시적으로 합성한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "focuson.intervention.off-task-ratio.percent=50",
    "focuson.intervention.off-task-ratio.minimum-observed-seconds=60"
})
@ActiveProfiles("test")
class InterventionApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private TransactionTemplate tx;
  @Autowired private UserRepository users;
  @Autowired private UserDeviceRepository devices;
  @Autowired private TokenFamilyRepository families;
  @Autowired private GoalRepository goals;
  @Autowired private LearningSessionRepository sessions;
  @Autowired private AnalysisRunRepository runs;
  @Autowired private UserConsentRepository consents;
  @Autowired private InterventionEventRepository events;
  @Autowired private InterventionDeliveryRepository deliveries;
  @Autowired private InterventionPolicyRepository states;
  @Autowired private com.capstone.backend.user.persistence.UserFocusSettingsRepository
      focusSettings;
  @Autowired private com.capstone.backend.intervention.persistence.ObservationContextRepository
      observationContexts;
  @Autowired private InterventionPolicyService policy;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  private HeartbeatPolicyEvaluator evaluator;
  @Autowired private com.capstone.backend.session.service.SessionHeartbeatService heartbeats;
  @Autowired private InterventionService service;
  @Autowired private com.zaxxer.hikari.HikariDataSource dataSource;
  @Autowired private com.capstone.backend.analysis.service.AnalysisRunStore analysisStore;
  @Autowired private tools.jackson.databind.ObjectMapper json;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private ForegroundPolicyContext foreground;
  @org.springframework.boot.test.web.server.LocalServerPort private int port;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "intervention-" + UUID.randomUUID());
    when(foreground.contains(anyLong(), anyLong(), anyString())).thenReturn(true);
    when(foreground.requireForRun(any(), anyLong(), any(), any())).thenAnswer(invocation -> {
      String raw = invocation.getArgument(2);
      return raw == null ? null : Long.parseLong(raw);
    });
  }

  @Test
  void actualHttpReturnsExistingArrayAndAckContract() throws Exception {
    var f = fixture(true);
    generate(f);
    long event = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst().getId();
    var client = java.net.http.HttpClient.newHttpClient();
    var list = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
        "http://127.0.0.1:" + port + path(f)))
        .header("Authorization", "Bearer " + f.account().accessToken()).GET().build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, list.statusCode());
    assertTrue(json.readTree(list.body()).path("data").isArray());
    var ack = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
        "http://127.0.0.1:" + port + ack(event)))
        .header("Authorization", "Bearer " + f.account().accessToken())
        .header("X-Device-Id", f.device()).header("Content-Type", "application/json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
            "{\"action\":\"DISMISSED\"}")).build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, ack.statusCode());
    assertEquals("DISMISSED", json.readTree(ack.body()).path("data").path("status").asString());
  }

  @Test
  void publicArrayAndAcknowledgementAreIndependentOfSessionAndDelivery() throws Exception {
    var f = fixture(true);
    generate(f);
    long event = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst().getId();
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].interventionId").value(Long.toString(event)))
        .andExpect(jsonPath("$.data[0].type").value("OFF_TASK"))
        .andExpect(jsonPath("$.data[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data[0].channels[0]").value("MOLE_UI"))
        .andExpect(jsonPath("$.data[0].policyVersion").value("v1"));
    mvc.perform(auth(post(ack(event)), f).header("X-Device-Id", f.principal().deviceId())
        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACKNOWLEDGED\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
        .andExpect(jsonPath("$.data.actedAt").isString());
    var first = service.acknowledge(f.principal(), f.device(), Long.toString(event),
        new Acknowledge(Action.ACKNOWLEDGED));
    var opposite = service.acknowledge(f.principal(), f.device(), Long.toString(event),
        new Acknowledge(Action.DISMISSED));
    assertEquals(first, opposite);
    assertEquals("RUNNING", sessions.findById(f.session()).orElseThrow().getStatus().name());
    assertEquals(null, events.findById(event).orElseThrow().getDeliveryConfirmedAt());
    evaluator.evaluate(f.principal().userId(), f.session());
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
  }

  /** 「이것도 공부야」는 확인과 따로 기록되고, 근거 판정 id와 이어지며, 휴식 제안에는 쓸 수 없다. */
  @Test
  void notOffTaskIsRecordedSeparatelyAndOnlyForOffTask() throws Exception {
    var f = fixture(true);
    generate(f);
    long event = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst().getId();
    mvc.perform(auth(post(ack(event)), f).header("X-Device-Id", f.principal().deviceId())
        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"NOT_OFF_TASK\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NOT_OFF_TASK"))
        .andExpect(jsonPath("$.data.actedAt").isString());
    // 첫 결정이 유지되고, 근거 판정 id로만 이어진다.
    var again = service.acknowledge(f.principal(), f.device(), Long.toString(event),
        new Acknowledge(Action.ACKNOWLEDGED));
    assertEquals(com.capstone.backend.intervention.dto.InterventionDtos.Status.NOT_OFF_TASK,
        again.status());
    org.junit.jupiter.api.Assertions.assertNotNull(
        events.findById(event).orElseThrow().getAnalysisRunId());
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("NOT_OFF_TASK"));
    // 휴식 제안은 딴짓 판정이 아니므로 오판 신고를 받지 않는다.
    jdbc.update("UPDATE intervention_events SET type='BREAK_SUGGESTION', status='PENDING', "
        + "acted_at=NULL, pending_slot=1 WHERE id=?", event);
    mvc.perform(auth(post(ack(event)), f).header("X-Device-Id", f.principal().deviceId())
        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"NOT_OFF_TASK\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertEquals("PENDING", jdbc.queryForObject(
        "SELECT status FROM intervention_events WHERE id=?", String.class, event));
  }

  /** 5분 이내 간격의 이탈이 3회면 이탈 알림 대신 근거가 붙은 휴식 제안을 한 번만 만든다(#301). */
  @Test
  void repeatedOffTaskCreatesOneBreakSuggestionWithEvidence() throws Exception {
    var f = fixture(true, 900);
    seedUnrelated(f);
    addEarlierEpisodes(f);
    evaluator.evaluate(f.principal().userId(), f.session());
    var created = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session());
    assertEquals(1, created.size());
    var event = created.getFirst();
    assertEquals(com.capstone.backend.intervention.dto.InterventionDtos.Type.BREAK_SUGGESTION,
        event.getType());
    assertEquals(3, event.getBreakRepeatCount());
    org.junit.jupiter.api.Assertions.assertNotNull(event.getAnalysisRunId());
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].type").value("BREAK_SUGGESTION"))
        .andExpect(jsonPath("$.data[0].breakEvidence.repeatCount").value(3))
        .andExpect(jsonPath("$.data[0].breakEvidence.windowMinutes").isNumber());
    // 같은 실행 구간에서는 다시 제안하지 않는다. 대기 알림을 정리한 뒤에도 휴식 제안은 늘지 않는다.
    jdbc.update("UPDATE intervention_events SET status='DISMISSED', acted_at=?, pending_slot=NULL,"
        + " delivery_confirmed_at=? WHERE id=?", java.sql.Timestamp.from(Instant.now()),
        java.sql.Timestamp.from(Instant.now().minusSeconds(600)), event.getId());
    addEarlierEpisodes(f);
    evaluator.evaluate(f.principal().userId(), f.session());
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).stream()
        .filter(value -> value.getType()
            == com.capstone.backend.intervention.dto.InterventionDtos.Type.BREAK_SUGGESTION)
        .count());
  }

  /** 휴식 제안 설정을 끄면 같은 반복 이탈에서도 이탈 알림만 만든다. */
  @Test
  void disabledBreakSuggestionFallsBackToOffTask() throws Exception {
    var f = fixture(true, 900);
    tx.executeWithoutResult(ignored -> focusSettings.save(
        com.capstone.backend.user.persistence.UserFocusSettingsEntity.create(
            f.principal().userId(), com.capstone.backend.user.persistence.UserFocusSettingsEntity
                .InterventionLevel.NORMAL, "MOLE_UI", false, false, Instant.now())));
    seedUnrelated(f);
    addEarlierEpisodes(f);
    evaluator.evaluate(f.principal().userId(), f.session());
    var created = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session());
    assertEquals(1, created.size());
    assertEquals(com.capstone.backend.intervention.dto.InterventionDtos.Type.OFF_TASK,
        created.getFirst().getType());
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].breakEvidence").doesNotExist());
  }

  /** 현재 열린 딴짓 구간 앞에 4분 간격으로 40초짜리 이탈 2개를 넣는다(보통 강도 반복 기준 30초). */
  private void addEarlierEpisodes(Fixture f) {
    tx.executeWithoutResult(ignored -> {
      var state = states.findById(f.session()).orElseThrow();
      var snapshot = json.readValue(state.getTimelineJson(),
          com.capstone.backend.intervention.service.OffTaskTimeline.Snapshot.class);
      Instant open = snapshot.spans().stream()
          .filter(span -> span.episode() == snapshot.openEpisode())
          .map(com.capstone.backend.intervention.service.OffTaskTimeline.Span::start)
          .min(Instant::compareTo).orElseThrow();
      var spans = new java.util.ArrayList<>(snapshot.spans());
      for (int index = 1; index <= 2; index++) {
        Instant start = open.minusSeconds(240L * index);
        spans.add(new com.capstone.backend.intervention.service.OffTaskTimeline.Span(
            1000L + index, start, start.plusSeconds(40)));
      }
      state.transition(json.writeValueAsString(
          new com.capstone.backend.intervention.service.OffTaskTimeline.Snapshot(
              snapshot.runningSince(), snapshot.context(), snapshot.changedAt(),
              snapshot.episode(), snapshot.pendingRun(), snapshot.latestRun(),
              snapshot.completedAt(), snapshot.latestReceivedAt(), snapshot.openEpisode(),
              spans, snapshot.offTaskRun(), snapshot.relatedTime(), snapshot.goalVersion())));
      states.saveAndFlush(state);
    });
  }

  @Test
  void unknownContextNeverGuessesLatestDeviceOrGenerates() throws Exception {
    var f = fixture(false);
    generate(f);
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
    assertFalse(states.existsById(f.session()));
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    assertTrue(foreground.resolve(f.session()).isEmpty());
  }

  @Test
  void ownershipHeadersAndValidationUseCommonHttpShape() throws Exception {
    var f = fixture(true);
    generate(f);
    var other = fixture(false);
    long event = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst().getId();
    mvc.perform(get(path(f))).andExpect(status().isUnauthorized());
    mvc.perform(auth(get(path(f)), other)).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    mvc.perform(auth(post(ack(event)), f).contentType(MediaType.APPLICATION_JSON)
        .content("{\"action\":\"ACKNOWLEDGED\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(auth(post(ack(event)), other).header("X-Device-Id", other.device())
        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACKNOWLEDGED\"}"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(auth(post(ack(event)), f).header("X-Device-Id", f.device())
        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DISPLAYED\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void thirtySecondExpiryDoesNotInventUserActionOrResetDelivery() throws Exception {
    var f = fixture(true);
    generate(f);
    var event = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst();
    jdbc.update("UPDATE intervention_events SET created_at=?, expires_at=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
        java.sql.Timestamp.from(Instant.now().minusSeconds(30)), event.getId());
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("EXPIRED"));
    var result = service.acknowledge(f.principal(), f.device(), event.getId().toString(),
        new Acknowledge(Action.ACKNOWLEDGED));
    assertEquals(Status.EXPIRED, result.status());
    assertEquals(null, result.actedAt());
    evaluator.evaluate(f.principal().userId(), f.session());
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
  }

  @Test
  void concurrentHeartbeatAndAcksGenerateAndApplyOnlyOnce() throws Exception {
    var f = fixture(true);
    seedUnrelated(f);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var jobs = List.of(executor.submit(() -> evaluator.evaluate(f.principal().userId(),
          f.session())), executor.submit(() -> evaluator.evaluate(f.principal().userId(),
          f.session())));
      for (var job : jobs) {
        job.get();
      }
    }
    var event = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst();
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.acknowledge(f.principal(), f.device(),
          event.getId().toString(), new Acknowledge(Action.ACKNOWLEDGED)));
      var second = executor.submit(() -> service.acknowledge(f.principal(), f.device(),
          event.getId().toString(), new Acknowledge(Action.DISMISSED)));
      assertEquals(first.get(), second.get());
    }
  }

  @Test
  void consentWithdrawalOrExecutionChangeHidesStalePending() throws Exception {
    var f = fixture(true);
    generate(f);
    jdbc.update("UPDATE learning_sessions SET last_resumed_at=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now()),
        f.session());
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    evaluator.evaluate(f.principal().userId(), f.session());
    assertEquals(Status.EXPIRED, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session())
        .getFirst().getStatus());
  }

  @Test
  void analysisStorePendingPreservesEpisodeAndLateResultCannotOverrideGuard() {
    var f = fixture(true);
    seedUnrelated(f);
    var next = observation(f, 2, 2L, null);
    var pending = analysisStore.prepare(f.principal(), f.session(), next);
    var frozen = snapshot(f);
    assertEquals(1L, frozen.openEpisode());
    assertEquals(pending.runId(), frozen.pendingRun());
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
    analysisStore.complete(f.principal(), pending, next, unrelatedDecision());
    assertEquals(1L, snapshot(f).openEpisode());
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
    var later = observation(f, 3, 3L, null);
    var delayed = analysisStore.prepare(f.principal(), f.session(), later);
    analysisStore.prepare(f.principal(), f.session(), observation(f, 4, 4L,
        com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus.EXCLUDED));
    analysisStore.complete(f.principal(), delayed, later, unrelatedDecision());
    assertEquals(null, snapshot(f).openEpisode());
    assertEquals(Status.EXPIRED,
        events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst().getStatus());
  }

  @Test
  void farTimeIncludesReanalysisBoundaryButExcludesWaiting() {
    var f = fixture(true);
    jdbc.update("UPDATE learning_sessions SET intervention_policy_version='v2' WHERE id=?",
        f.session());
    final Instant start = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        .minusSeconds(45);
    var far = farDecision();
    recordObservation(f, 1, start, far);
    tx.executeWithoutResult(status -> policy.heartbeat(
        sessions.findById(f.session()).orElseThrow(), start.plusSeconds(15)));
    recordObservation(f, 2, start.plusSeconds(20), start.plusSeconds(30), far);
    var state = states.findById(f.session()).orElseThrow();
    assertEquals(20000L, state.getFarDurationMs());
    assertEquals(start.plusSeconds(30), state.getFarCheckpointAt());

    recordObservation(f, 3, start.plusSeconds(32), start.plusSeconds(36),
        AnalysisDecision.unavailable());
    state = states.findById(f.session()).orElseThrow();
    assertEquals(22000L, state.getFarDurationMs());
    assertEquals(null, state.getFarCheckpointAt());
    recordObservation(f, 4, start.plusSeconds(39), far);
    assertEquals(22000L, states.findById(f.session()).orElseThrow().getFarDurationMs());
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
  }

  @Test
  void interruptedConsentKeepsOldFarStoppedUntilFreshAnalysis() {
    var f = fixture(true);
    jdbc.update("UPDATE learning_sessions SET intervention_policy_version='v2' WHERE id=?",
        f.session());
    Instant start = Instant.now().minusSeconds(20);
    recordObservation(f, 1, start, farDecision());
    tx.executeWithoutResult(status -> {
      var session = sessions.findById(f.session()).orElseThrow();
      policy.consentWithdrawn(session, start.plusSeconds(5));
      policy.heartbeat(session, start.plusSeconds(10));
    });
    assertEquals(null, states.findById(f.session()).orElseThrow().getFarCheckpointAt());
    recordObservation(f, 2, start.plusSeconds(15), farDecision());
    assertEquals(0L, states.findById(f.session()).orElseThrow().getFarDurationMs());
  }

  @Test
  void reanalysisCreditsThresholdOnceAndWaitsForConfirmedFar() {
    var f = fixture(true);
    jdbc.update("UPDATE learning_sessions SET intervention_policy_version='v2' WHERE id=?",
        f.session());
    recordObservation(f, 1, Instant.now().minusSeconds(3), farDecision());
    jdbc.update("UPDATE intervention_policy_states SET far_duration_ms=598000 WHERE session_id=?",
        f.session());

    var input = observation(f, 2, 2L, null);
    final var prepared = analysisStore.prepare(f.principal(), f.session(), input);
    assertEquals(600000L, states.findById(f.session()).orElseThrow().getFarDurationMs());
    org.junit.jupiter.api.Assertions.assertThrows(
        com.capstone.backend.common.api.ApiException.class,
        () -> analysisStore.prepare(f.principal(), f.session(), input));
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());

    analysisStore.complete(f.principal(), prepared, input, farDecision());
    evaluator.evaluate(f.principal().userId(), f.session());
    var prompted = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session());
    assertEquals(1, prompted.size());
    assertEquals(Type.GOAL_ADD_PROMPT, prompted.getFirst().getType());
  }

  private AnalysisDecision farDecision() {
    return new AnalysisDecision(
        com.capstone.backend.analysis.dto.AnalysisDtos.Relation.RELATED, .9,
        com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode.SEMANTIC_GOAL_MATCH,
        com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus.COMPLETED, "qa", "qa",
        com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus.FAR);
  }

  @Test
  void confirmedFarCreatesOnePromptAndInconclusiveTimeIsExcluded() throws Exception {
    var f = fixture(true);
    jdbc.update("UPDATE learning_sessions SET intervention_policy_version='v2' WHERE id=?",
        f.session());
    var first = observation(f, 1, 1L, null);
    var inconclusive = analysisStore.prepare(f.principal(), f.session(), first);
    var notEnough = new AnalysisDecision(
        com.capstone.backend.analysis.dto.AnalysisDtos.Relation.RELATED, .9,
        com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode.SEMANTIC_GOAL_MATCH,
        com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus.COMPLETED, "qa", "qa",
        com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus.NOT_ENOUGH);
    analysisStore.complete(f.principal(), inconclusive, first, notEnough);
    assertEquals(null, states.findById(f.session()).orElseThrow().getFarCheckpointAt());

    var second = observation(f, 2, 2L, null);
    var confirmed = analysisStore.prepare(f.principal(), f.session(), second);
    var far = farDecision();
    analysisStore.complete(f.principal(), confirmed, second, far);
    Instant checkpoint = states.findById(f.session()).orElseThrow().getFarCheckpointAt();
    tx.executeWithoutResult(status -> policy.heartbeat(
        sessions.findById(f.session()).orElseThrow(), checkpoint.plusSeconds(599)));
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());

    tx.executeWithoutResult(status -> policy.heartbeat(
        sessions.findById(f.session()).orElseThrow(), checkpoint.plusSeconds(600)));
    var event = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst();
    assertEquals(Type.GOAL_ADD_PROMPT, event.getType());
    assertEquals(confirmed.runId(), event.getAnalysisRunId());

    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].type").value("GOAL_ADD_PROMPT"));
    String deliveryPath = "/api/v1/interventions/" + event.getId() + "/deliveries";
    String delivery = "{\"clientDeliveryId\":\"" + UUID.randomUUID()
        + "\",\"dispatchStatus\":\"SENT\",\"channels\":[\"MOLE_UI\"]}";
    mvc.perform(auth(post(deliveryPath), f).header("X-Device-Id", f.device())
            .contentType(MediaType.APPLICATION_JSON).content(delivery))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.cooldownUntil").doesNotExist());
    assertEquals(Status.PENDING, events.findById(event.getId()).orElseThrow().getStatus());
    mvc.perform(auth(post(ack(event.getId())), f).header("X-Device-Id", f.device())
            .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACKNOWLEDGED\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));
    tx.executeWithoutResult(status -> policy.heartbeat(
        sessions.findById(f.session()).orElseThrow(), checkpoint.plusSeconds(601)));
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
  }

  @Test
  void expiredGoalPromptWithoutReceiptDoesNotSuppressOffTaskAlert() {
    var f = fixture(true);
    jdbc.update("UPDATE learning_sessions SET intervention_policy_version='v2' WHERE id=?",
        f.session());
    var session = sessions.findById(f.session()).orElseThrow();
    var prompt = InterventionEventEntity.goalAddPrompt(session.getId(), 1, "[\"MOLE_UI\"]",
        "v2", session.getLastResumedAt(), Instant.now().minusSeconds(60));
    prompt.bindContext(f.observationContext());
    prompt.expire();
    events.saveAndFlush(prompt);

    var actualContext = new ForegroundPolicyContext(observationContexts, sessions, runs, events,
        deliveries).resolve(f.session()).orElseThrow();
    assertFalse(actualContext.deliveryUnconfirmed());

    generate(f);
    assertEquals(List.of(Type.GOAL_ADD_PROMPT, Type.OFF_TASK),
        events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).stream()
            .map(InterventionEventEntity::getType).toList());
  }

  @Test
  void untrustedCurrentInterruptsPreviousEpisodeInsteadOfAccruingUnknownTime() {
    var f = fixture(true);
    seedUnrelated(f);
    var legacy = observation(f, 2, null, null);
    // 번호 모드 이후의 legacy는 current를 대체하지 않는다. 별도 legacy fixture로 전환한다.
    jdbc.update("UPDATE analysis_runs SET observation_sequence=NULL WHERE session_id=?",
        f.session());
    jdbc.update("UPDATE analysis_runs SET observed_at=? WHERE session_id=?",
        java.sql.Timestamp.from(Instant.now().plusSeconds(3600)), f.session());
    analysisStore.prepare(f.principal(), f.session(), legacy);
    assertTrue(runs.findCurrent(f.session(), f.principal().deviceId()).orElseThrow()
        .isOrderingUntrusted());
    assertEquals(null, snapshot(f).openEpisode());
    var recovered = observation(f, 3, 1L, null);
    var prepared = analysisStore.prepare(f.principal(), f.session(), recovered);
    analysisStore.complete(f.principal(), prepared, recovered, unrelatedDecision());
    assertEquals(2L, snapshot(f).openEpisode());
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
  }

  private com.capstone.backend.intervention.service.OffTaskTimeline.Snapshot snapshot(Fixture f) {
    return json.readValue(states.findById(f.session()).orElseThrow().getTimelineJson(),
        com.capstone.backend.intervention.service.OffTaskTimeline.Snapshot.class);
  }

  private Request observation(Fixture f, long sequence, Long global,
      com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus guard) {
    return new Request(UUID.randomUUID().toString(), ContentSource.DESKTOP_APP_OCR,
        Instant.now(), BigDecimal.valueOf(sequence), guard,
        guard == null ? new com.capstone.backend.analysis.dto.AnalysisDtos.Metadata(
            "Xcode", "synthetic", null, null) : null,
        guard == null ? new com.capstone.backend.analysis.dto.AnalysisDtos.ContentPayload(
            "synthetic test") : null, global == null ? null : BigDecimal.valueOf(global),
        Long.toString(f.observationContext()));
  }

  private AnalysisDecision unrelatedDecision() {
    return new AnalysisDecision(com.capstone.backend.analysis.dto.AnalysisDtos.Relation.UNRELATED,
        .9, com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode.SEMANTIC_GOAL_MISMATCH,
        com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus.COMPLETED, "qa", "qa");
  }

  @Test
  void policyFailureAfterCommitDoesNotDiscardValidHeartbeat() {
    var f = fixture(false);
    org.mockito.Mockito.doThrow(new IllegalStateException("synthetic failure"))
        .when(evaluator).evaluate(f.principal().userId(), f.session());
    var response = heartbeats.heartbeat(f.principal(), f.device(), Long.toString(f.session()),
        new com.capstone.backend.session.dto.SessionDtos.HeartbeatRequest(Instant.now(), 100L));
    assertEquals(100, response.accumulatedActiveMs());
    var saved = sessions.findById(f.session()).orElseThrow();
    assertEquals(100, saved.getAccumulatedActiveMs());
    assertTrue(saved.getLastHeartbeatAt() != null);
  }

  @Test
  void heartbeatEvaluationWorksAfterCleanupWithSingleConnectionPool() {
    var f = fixture(true);
    seedUnrelated(f);
    int previousMax = dataSource.getMaximumPoolSize();
    long previousTimeout = dataSource.getConnectionTimeout();
    try {
      dataSource.getHikariConfigMXBean().setMaximumPoolSize(1);
      dataSource.getHikariConfigMXBean().setConnectionTimeout(1000);
      dataSource.getHikariPoolMXBean().softEvictConnections();
      heartbeats.heartbeat(f.principal(), f.device(), Long.toString(f.session()),
          new com.capstone.backend.session.dto.SessionDtos.HeartbeatRequest(Instant.now(), 100L));
      assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
    } finally {
      dataSource.getHikariConfigMXBean().setMaximumPoolSize(previousMax);
      dataSource.getHikariConfigMXBean().setConnectionTimeout(previousTimeout);
    }
  }

  private void generate(Fixture f) {
    seedUnrelated(f);
    evaluator.evaluate(f.principal().userId(), f.session());
  }

  /** v2: 판단 보류가 오면 보류 전에 만든 미표시 알림은 만료되고, 멈춘 동안 새 알림도 없다. */
  @Test
  void v2UncertainExpiresPendingAlertAndBlocksNewOnesWhileOnHold() throws Exception {
    var f = fixture(true);
    jdbc.update("UPDATE learning_sessions SET intervention_policy_version='v2' WHERE id=?",
        f.session());
    generate(f);
    long event = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst().getId();
    assertEquals("PENDING", jdbc.queryForObject(
        "SELECT status FROM intervention_events WHERE id=?", String.class, event));
    tx.executeWithoutResult(ignored -> {
      users.findByIdForUpdate(f.principal().userId()).orElseThrow();
      var session = sessions.findOwnedForUpdate(f.session(), f.principal().userId()).orElseThrow();
      Instant at = Instant.now();
      var request = new Request(UUID.randomUUID().toString(), ContentSource.DESKTOP_APP_OCR,
          at, BigDecimal.valueOf(2), null, null, null, BigDecimal.valueOf(2));
      var run = AnalysisRunEntity.reserve(session,
          devices.getReferenceById(f.principal().deviceId()), request, at);
      run.bindContext(f.observationContext());
      runs.saveAndFlush(run);
      policy.prepared(session, run, true, false, at);
      run.complete(AnalysisDecision.unavailable());
      policy.completed(session, run, at);
    });
    assertEquals("EXPIRED", jdbc.queryForObject(
        "SELECT status FROM intervention_events WHERE id=?", String.class, event));
    var snapshot = json.readValue(states.findById(f.session()).orElseThrow().getTimelineJson(),
        com.capstone.backend.intervention.service.OffTaskTimeline.Snapshot.class);
    // 딴짓 구간은 끊기지 않고 멈춘다.
    org.junit.jupiter.api.Assertions.assertNotNull(snapshot.openEpisode());
    jdbc.update("UPDATE intervention_events SET delivery_confirmed_at=created_at WHERE id=?",
        event);
    evaluator.evaluate(f.principal().userId(), f.session());
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
  }

  @Test
  void heartbeatCreatesOneOffTaskEventFromRatioAlone() throws Exception {
    var f = fixture(true);
    jdbc.update("UPDATE learning_sessions SET intervention_policy_version='v2' WHERE id=?",
        f.session());
    Instant reference = Instant.now();
    var related = new AnalysisDecision(
        com.capstone.backend.analysis.dto.AnalysisDtos.Relation.RELATED, .9,
        com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode.SEMANTIC_GOAL_MATCH,
        com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus.COMPLETED, "qa", "qa");
    recordObservation(f, 1, reference.minusSeconds(80), unrelatedDecision());
    recordObservation(f, 2, reference.minusSeconds(55), related);
    recordObservation(f, 3, reference.minusSeconds(43), related);
    recordObservation(f, 4, reference.minusSeconds(18), unrelatedDecision());
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
    final var latest = runs.findCurrent(f.session(), f.principal().deviceId()).orElseThrow();
    heartbeats.heartbeat(f.principal(), f.device(), Long.toString(f.session()),
        new com.capstone.backend.session.dto.SessionDtos.HeartbeatRequest(reference, 100L));
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
    evaluator.evaluate(f.principal().userId(), f.session());
    var result = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session());
    assertEquals(1, result.size());
    assertEquals(latest.getId(), result.getFirst().getAnalysisRunId());
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].type").value("OFF_TASK"));
  }

  @Test
  void heartbeatDoesNotCreateInterventionFromPreviousGoalVersion() throws Exception {
    var f = fixture(true);
    seedUnrelated(f);
    changeGoalVersion(f);
    heartbeats.heartbeat(f.principal(), f.device(), Long.toString(f.session()),
        new com.capstone.backend.session.dto.SessionDtos.HeartbeatRequest(Instant.now(), 100L));
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
  }

  @Test
  void heartbeatDoesNotSuggestBreakFromPreviousGoalVersion() throws Exception {
    var f = fixture(true, 900);
    jdbc.update("UPDATE learning_sessions SET last_heartbeat_at=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(2)), f.session());
    seedUnrelated(f);
    addEarlierEpisodes(f);
    changeGoalVersion(f);
    heartbeats.heartbeat(f.principal(), f.device(), Long.toString(f.session()),
        new com.capstone.backend.session.dto.SessionDtos.HeartbeatRequest(Instant.now(), 100L));
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
  }

  @Test
  void pendingInterventionFromPreviousGoalVersionIsNotPresentable() throws Exception {
    var f = fixture(true);
    generate(f);
    final long id = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst().getId();
    changeGoalVersion(f);
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    evaluator.evaluate(f.principal().userId(), f.session());
    assertEquals(2L, snapshot(f).goalVersion());
    assertTrue(snapshot(f).spans().isEmpty());
    assertEquals(com.capstone.backend.intervention.dto.InterventionDtos.Status.EXPIRED,
        events.findById(id).orElseThrow().getStatus());
    long revision = states.findById(f.session()).orElseThrow().getRevision();
    evaluator.evaluate(f.principal().userId(), f.session());
    assertEquals(revision, states.findById(f.session()).orElseThrow().getRevision());

    var sent = new com.capstone.backend.intervention.dto.InterventionDtos.Delivery(
        UUID.randomUUID().toString(),
        com.capstone.backend.intervention.dto.InterventionDtos.DispatchStatus.SENT,
        List.of(com.capstone.backend.session.dto.SessionDtos.NotificationChannel.MOLE_UI));
    String first = postDelivery(f, id, sent);
    assertEquals(json.readTree(first).path("data"),
        json.readTree(postDelivery(f, id, sent)).path("data"));
    var stored = events.findById(id).orElseThrow();
    assertEquals(com.capstone.backend.intervention.dto.InterventionDtos.Status.EXPIRED,
        stored.getStatus());
    assertEquals(stored.getDeliveryConfirmedAt().plusSeconds(300),
        Instant.parse(json.readTree(first).at("/data/cooldownUntil").asString()));
  }

  @Test
  void firstObservationForChangedGoalsRestartsTheOffTaskTimeline() throws Exception {
    var f = fixture(true);
    seedUnrelated(f);
    changeGoalVersion(f);
    Instant started = Instant.now();
    recordObservation(f, 2, started, unrelatedDecision());
    var current = snapshot(f);
    assertEquals(2, current.goalVersion());
    assertEquals(1, current.spans().size());
    assertEquals(started, current.spans().getFirst().start());
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());

    recordObservation(f, 3, started.plusSeconds(61), unrelatedDecision());
    var alerts = events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session());
    assertEquals(1, alerts.size());
    assertEquals(Type.OFF_TASK, alerts.getFirst().getType());
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].type").value("OFF_TASK"));
  }

  private String postDelivery(Fixture f, long id,
      com.capstone.backend.intervention.dto.InterventionDtos.Delivery delivery) throws Exception {
    return mvc.perform(auth(post("/api/v1/interventions/" + id + "/deliveries"), f)
            .header("X-Device-Id", f.device()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(delivery)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  private void changeGoalVersion(Fixture f) throws Exception {
    var goal = tx.execute(ignored -> goals.save(GoalEntity.create(
        users.getReferenceById(f.principal().userId()), "새 목표",
        GoalEntity.ConfirmationSource.MANUAL_CONFIRMED, Instant.now())));
    String body = json.writeValueAsString(java.util.Map.of(
        "goalId", goal.getId().toString(), "goalVersion", 1));
    mvc.perform(auth(post("/api/v1/sessions/" + f.session() + "/goals"), f)
            .header("X-Device-Id", f.device()).contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.goalVersion").value(2));
  }

  private void seedUnrelated(Fixture f) {
    recordObservation(f, 1, Instant.now().minusSeconds(100), unrelatedDecision());
  }

  private void recordObservation(Fixture f, long number, Instant at, AnalysisDecision decision) {
    recordObservation(f, number, at, at, decision);
  }

  private void recordObservation(Fixture f, long number, Instant at, Instant completedAt,
      AnalysisDecision decision) {
    tx.executeWithoutResult(ignored -> {
      users.findByIdForUpdate(f.principal().userId()).orElseThrow();
      var session = sessions.findOwnedForUpdate(f.session(), f.principal().userId()).orElseThrow();
      var sequence = BigDecimal.valueOf(number);
      var request = new Request(UUID.randomUUID().toString(), ContentSource.DESKTOP_APP_OCR,
          at, sequence, null, null, null, sequence);
      var run = AnalysisRunEntity.reserve(session,
          devices.getReferenceById(f.principal().deviceId()), request, at);
      run.bindContext(f.observationContext());
      runs.saveAndFlush(run);
      policy.prepared(session, run, true, false, at);
      run.complete(decision);
      policy.completed(session, run, completedAt);
    });
  }

  private Fixture fixture(boolean binding) {
    return fixture(binding, 150);
  }

  /** startedSecondsAgo: 세션 시작을 몇 초 전으로 둘지. 지난 이탈을 넣는 테스트는 그보다 앞서 시작한다. */
  private Fixture fixture(boolean binding, long startedSecondsAgo) {
    var account = login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), "test",
        "http://127.0.0.1:49152", new DesktopDevice(UUID.randomUUID().toString(), "QA", "MACOS")));
    var result = tx.execute(ignored -> {
      var user = users.findById(Long.parseLong(account.user().userId())).orElseThrow();
      var device = devices.findById(Long.parseLong(account.device().deviceId())).orElseThrow();
      var family = families.findByDeviceAndStatus(device.getId(), TokenFamilyEntity.Status.ACTIVE)
          .orElseThrow();
      for (var type : new ConsentType[] {ConsentType.TERMS_OF_SERVICE, ConsentType.PRIVACY_POLICY,
          ConsentType.SCREEN_ANALYSIS}) {
        consents.save(UserConsentEntity.create(user, type, true, "2026-09-17", "QA",
            Instant.now()));
      }
      var goal = goals.save(GoalEntity.create(user, "합성 테스트",
          GoalEntity.ConfirmationSource.MANUAL_CONFIRMED, Instant.now()));
      var session = sessions.saveAndFlush(LearningSessionEntity.create(user, goal, device,
          1800000, "Asia/Seoul", LocalDate.now(), "{\"interventionLevel\":\"NORMAL\","
              + "\"notificationChannels\":[\"MOLE_UI\"],\"cameraAnalysisEnabled\":false}",
          "v1", Instant.now().minusSeconds(startedSecondsAgo)));
      var observationContext = observationContexts.saveAndFlush(
          com.capstone.backend.intervention.persistence.ObservationContextEntity.create(
              session.getId(), device.getId(), device.getId(),
              com.capstone.backend.intervention.dto.ObservationContextDtos.Observer.DESKTOP,
              UUID.randomUUID(), 1, session.getLastResumedAt(), Instant.now()));
      return new Fixture(account, new FocusOnPrincipal(user.getId(), device.getId(), family.getId(),
          device.getDeviceType()), session.getId(), observationContext.getId());
    });
    if (binding) {
      when(foreground.resolve(result.session())).thenReturn(Optional.of(
          new ForegroundPolicyContext.Binding(result.principal().deviceId(),
              Long.toString(result.observationContext()),
              Instant.now().minusSeconds(120), true, null, false)));
    }
    return result;
  }

  private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, Fixture f) {
    return request.header("Authorization", "Bearer " + f.account().accessToken());
  }

  private String path(Fixture f) {
    return "/api/v1/sessions/" + f.session() + "/interventions";
  }

  private String ack(long id) {
    return "/api/v1/interventions/" + id + "/acknowledgements";
  }

  private record Fixture(LoginResponse account, FocusOnPrincipal principal, long session,
      long observationContext) {
    String device() {
      return Long.toString(principal.deviceId());
    }
  }
}
