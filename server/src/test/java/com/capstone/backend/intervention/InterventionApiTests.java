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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
  @Autowired private InterventionPolicyRepository states;
  @Autowired private InterventionPolicyService policy;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  private HeartbeatPolicyEvaluator evaluator;
  @Autowired private com.capstone.backend.session.service.SessionHeartbeatService heartbeats;
  @Autowired private InterventionService service;
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

  @Test
  void unknownContextNeverGuessesLatestDeviceOrGenerates() throws Exception {
    var f = fixture(false);
    generate(f);
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
    assertFalse(states.existsById(f.session()));
    mvc.perform(auth(get(path(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    assertTrue(new ForegroundPolicyContext().resolve(f.session()).isEmpty());
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
    var next = observation(2, 2L, null);
    var pending = analysisStore.prepare(f.principal(), f.session(), next);
    var frozen = snapshot(f);
    assertEquals(1L, frozen.openEpisode());
    assertEquals(pending.runId(), frozen.pendingRun());
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
    analysisStore.complete(f.principal(), pending, next, unrelatedDecision());
    assertEquals(1L, snapshot(f).openEpisode());
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
    var later = observation(3, 3L, null);
    var delayed = analysisStore.prepare(f.principal(), f.session(), later);
    analysisStore.prepare(f.principal(), f.session(), observation(4, 4L,
        com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus.EXCLUDED));
    analysisStore.complete(f.principal(), delayed, later, unrelatedDecision());
    assertEquals(null, snapshot(f).openEpisode());
    assertEquals(Status.EXPIRED,
        events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst().getStatus());
  }

  @Test
  void untrustedCurrentInterruptsPreviousEpisodeInsteadOfAccruingUnknownTime() {
    var f = fixture(true);
    seedUnrelated(f);
    var legacy = observation(2, null, null);
    // 번호 모드 이후의 legacy는 current를 대체하지 않는다. 별도 legacy fixture로 전환한다.
    jdbc.update("UPDATE analysis_runs SET observation_sequence=NULL WHERE session_id=?",
        f.session());
    jdbc.update("UPDATE analysis_runs SET observed_at=? WHERE session_id=?",
        java.sql.Timestamp.from(Instant.now().plusSeconds(3600)), f.session());
    analysisStore.prepare(f.principal(), f.session(), legacy);
    assertTrue(runs.findCurrent(f.session(), f.principal().deviceId()).orElseThrow()
        .isOrderingUntrusted());
    assertEquals(null, snapshot(f).openEpisode());
    var recovered = observation(3, 1L, null);
    var prepared = analysisStore.prepare(f.principal(), f.session(), recovered);
    analysisStore.complete(f.principal(), prepared, recovered, unrelatedDecision());
    assertEquals(2L, snapshot(f).openEpisode());
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
  }

  private com.capstone.backend.intervention.service.OffTaskTimeline.Snapshot snapshot(Fixture f) {
    return json.readValue(states.findById(f.session()).orElseThrow().getTimelineJson(),
        com.capstone.backend.intervention.service.OffTaskTimeline.Snapshot.class);
  }

  private Request observation(long sequence, Long global,
      com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus guard) {
    return new Request(UUID.randomUUID().toString(), ContentSource.DESKTOP_APP_OCR,
        Instant.now(), BigDecimal.valueOf(sequence), guard,
        guard == null ? new com.capstone.backend.analysis.dto.AnalysisDtos.Metadata(
            "Xcode", "synthetic", null, null) : null,
        guard == null ? new com.capstone.backend.analysis.dto.AnalysisDtos.ContentPayload(
            "synthetic test") : null, global == null ? null : BigDecimal.valueOf(global));
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

  private void generate(Fixture f) {
    seedUnrelated(f);
    evaluator.evaluate(f.principal().userId(), f.session());
  }

  private void seedUnrelated(Fixture f) {
    tx.executeWithoutResult(ignored -> {
      users.findByIdForUpdate(f.principal().userId()).orElseThrow();
      var session = sessions.findOwnedForUpdate(f.session(), f.principal().userId()).orElseThrow();
      Instant at = Instant.now().minusSeconds(100);
      var request = new Request(UUID.randomUUID().toString(), ContentSource.DESKTOP_APP_OCR,
          at, BigDecimal.ONE, null, null, null, BigDecimal.ONE);
      var run = runs.saveAndFlush(AnalysisRunEntity.reserve(session,
          devices.getReferenceById(f.principal().deviceId()), request, at));
      policy.prepared(session, run, true, false, at);
      run.complete(new AnalysisDecision(
          com.capstone.backend.analysis.dto.AnalysisDtos.Relation.UNRELATED, .9,
          com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode.SEMANTIC_GOAL_MISMATCH,
          com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus.COMPLETED, "qa", "qa"));
      policy.completed(session, run, at);
    });
  }

  private Fixture fixture(boolean binding) {
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
          "v1", Instant.now().minusSeconds(150)));
      return new Fixture(account, new FocusOnPrincipal(user.getId(), device.getId(), family.getId(),
          device.getDeviceType()), session.getId());
    });
    if (binding) {
      when(foreground.resolve(result.session())).thenReturn(Optional.of(
          new ForegroundPolicyContext.Binding(result.principal().deviceId(), "synthetic-context",
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

  private record Fixture(LoginResponse account, FocusOnPrincipal principal, long session) {
    String device() {
      return Long.toString(principal.deviceId());
    }
  }
}
