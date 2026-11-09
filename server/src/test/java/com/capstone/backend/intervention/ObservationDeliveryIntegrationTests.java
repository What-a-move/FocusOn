package com.capstone.backend.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.analysis.dto.AnalysisDtos;
import com.capstone.backend.analysis.persistence.AnalysisRunEntity;
import com.capstone.backend.analysis.persistence.AnalysisRunRepository;
import com.capstone.backend.analysis.service.AnalysisDecision;
import com.capstone.backend.analysis.service.AnalysisRunStore;
import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.dto.PairingDtos.ClaimRequest;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.auth.service.PairingService;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.api.RequestIdFilter;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.goal.persistence.GoalRepository;
import com.capstone.backend.intervention.dto.InterventionDtos;
import com.capstone.backend.intervention.dto.ObservationContextDtos;
import com.capstone.backend.intervention.dto.ObservationContextDtos.Observer;
import com.capstone.backend.intervention.persistence.InterventionEventRepository;
import com.capstone.backend.intervention.persistence.InterventionPolicyRepository;
import com.capstone.backend.intervention.persistence.ObservationContextRepository;
import com.capstone.backend.intervention.service.ForegroundPolicyContext;
import com.capstone.backend.intervention.service.HeartbeatPolicyEvaluator;
import com.capstone.backend.intervention.service.InterventionDeliveryService;
import com.capstone.backend.intervention.service.InterventionPolicyService;
import com.capstone.backend.intervention.service.InterventionService;
import com.capstone.backend.intervention.service.ObservationContextService;
import com.capstone.backend.intervention.service.OffTaskTimeline;
import com.capstone.backend.session.dto.SessionDtos;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.service.SessionHeartbeatService;
import com.capstone.backend.user.persistence.UserConsentEntity;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserConsentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** 전경과 receipt는 실제 저장/인증 경로를 사용하며 외부 AI/학교 모델은 호출하지 않는다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ObservationDeliveryIntegrationTests {
  @Autowired private WebApplicationContext web;
  @Autowired private LoginService login;
  @Autowired private PairingService pairing;
  @Autowired private TransactionTemplate tx;
  @Autowired private UserRepository users;
  @Autowired private UserDeviceRepository devices;
  @Autowired private TokenFamilyRepository families;
  @Autowired private GoalRepository goals;
  @Autowired private LearningSessionRepository sessions;
  @Autowired private UserConsentRepository consents;
  @Autowired private ObservationContextService contexts;
  @Autowired private ObservationContextRepository contextRows;
  @Autowired private ForegroundPolicyContext foreground;
  @Autowired private AnalysisRunRepository runs;
  @Autowired private AnalysisRunStore store;
  @Autowired private InterventionPolicyService policy;
  @Autowired private InterventionPolicyRepository states;
  @Autowired private InterventionEventRepository events;
  @Autowired private InterventionService interventions;
  @Autowired private InterventionDeliveryService deliveries;
  @Autowired private SessionHeartbeatService heartbeat;
  @Autowired private HeartbeatPolicyEvaluator evaluator;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @MockitoBean private GoogleOauthClient google;
  private MockMvc mvc;
  @org.springframework.boot.test.web.server.LocalServerPort private int port;

  @BeforeEach
  void setUp() {
    when(google.exchange(any())).thenAnswer(invocation -> "context-" + UUID.randomUUID());
    mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
  }

  @Test
  void httpPutGetAndRetryPreserveIdentityAndDoNotRenew() throws Exception {
    var f = fixture();
    var input = contextRequest(1, Observer.DESKTOP);
    var response = mvc.perform(auth(put(contextPath(f)), f).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(input)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.observer").value("DESKTOP"))
        .andExpect(jsonPath("$.data.observerDeviceId").value(f.device()))
        .andExpect(jsonPath("$.data.leaseRemainingMs").value(30000)).andReturn();
    String id = json.readTree(response.getResponse().getContentAsString())
        .path("data").path("observationContextId").asString();
    Instant until = contextRows.findById(Long.parseLong(id)).orElseThrow().getValidUntil();
    var retry = contexts.put(f.principal(), f.device(), f.sessionString(), input);
    assertEquals(id, retry.observationContextId());
    mvc.perform(auth(get(contextPath(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.observationContextId").value(id));
    assertEquals(until, contextRows.findById(Long.parseLong(id)).orElseThrow().getValidUntil());
    var conflict = new ObservationContextDtos.Put(input.clientContextId(), BigDecimal.ONE,
        Observer.NONE);
    mvc.perform(auth(put(contextPath(f)), f).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(conflict))).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    mvc.perform(auth(put(contextPath(f)), f).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(contextRequest(1, Observer.NONE))))
        .andExpect(status().isConflict());
  }

  @Test
  void headersOwnershipAndCounterBoundariesUseCommonErrors() throws Exception {
    var f = fixture();
    var other = fixture();
    mvc.perform(get(contextPath(f)).header("Authorization", "Bearer " + f.account().accessToken()))
        .andExpect(status().isBadRequest());
    mvc.perform(auth(get(contextPath(f)), other)).andExpect(status().isNotFound());
    mvc.perform(auth(get(contextPath(f)), f)).andExpect(status().isNoContent());
    for (String sequence : List.of("0", "-1", "1.5", "9007199254740992")) {
      String body = "{\"clientContextId\":\"" + UUID.randomUUID()
          + "\",\"observer\":\"DESKTOP\",\"foregroundSequence\":" + sequence + "}";
      mvc.perform(auth(put(contextPath(f)), f).contentType(MediaType.APPLICATION_JSON)
          .content(body))
          .andExpect(status().isBadRequest());
    }
    contexts.put(f.principal(), f.device(), f.sessionString(),
        contextRequest(9007199254740991L, Observer.NONE));
    mvc.perform(auth(get(contextPath(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.observer").value("NONE"))
        .andExpect(jsonPath("$.data.observerDeviceId").doesNotExist());
    tx.executeWithoutResult(ignored -> assertTrue(foreground.resolve(f.session()).isEmpty()));
  }

  @Test
  void extensionIsInferredAndUnlinkRemovesUsability() throws Exception {
    var f = fixture();
    var linked = pairing.claim(new ClaimRequest(pairing.issue(f.principal()).codeDisplay(),
        UUID.randomUUID().toString()), "127.0.0.182");
    var item = contexts.put(f.principal(), f.device(), f.sessionString(),
        contextRequest(1, Observer.EXTENSION));
    assertEquals(linked.device().deviceId(), item.observerDeviceId());
    mvc.perform(get(contextPath(f)).header("Authorization", "Bearer " + linked.accessToken())
        .header("X-Device-Id", linked.device().deviceId())).andExpect(status().isOk());
    tx.executeWithoutResult(ignored -> devices.findById(Long.parseLong(linked.device().deviceId()))
        .orElseThrow().setStatus(
            com.capstone.backend.auth.persistence.UserDeviceEntity.Status.REVOKED));
    mvc.perform(auth(get(contextPath(f)), f)).andExpect(status().isNoContent());
  }

  @Test
  void expiredLeaseIsNotResurrectedByHeartbeatAndNewContextRecovers() {
    var f = fixture();
    var first = contexts.put(f.principal(), f.device(), f.sessionString(),
        contextRequest(1, Observer.DESKTOP));
    jdbc.update("UPDATE session_observation_contexts SET created_at=?,valid_until=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(40)),
        java.sql.Timestamp.from(Instant.now().minusSeconds(10)),
        Long.parseLong(first.observationContextId()));
    Instant expired = contextRows.findById(Long.parseLong(first.observationContextId()))
        .orElseThrow().getValidUntil();
    var response = heartbeat.heartbeat(f.principal(), f.device(), f.sessionString(),
        new SessionDtos.HeartbeatRequest(Instant.now(), 100L, first.observationContextId()));
    assertEquals(100L, response.accumulatedActiveMs());
    assertEquals(expired, contextRows.findById(Long.parseLong(first.observationContextId()))
        .orElseThrow().getValidUntil());
    assertTrue(contexts.get(f.principal(), f.device(), f.sessionString()).isEmpty());
    var next = contexts.put(f.principal(), f.device(), f.sessionString(),
        contextRequest(2, Observer.DESKTOP));
    assertFalse(first.observationContextId().equals(next.observationContextId()));
    assertTrue(contexts.get(f.principal(), f.device(), f.sessionString()).isPresent());
  }

  @Test
  void onlyAcceptedHeartbeatRenewsAndReplayDoesNot() {
    var f = fixture();
    var item = contexts.put(f.principal(), f.device(), f.sessionString(),
        contextRequest(1, Observer.DESKTOP));
    jdbc.update("UPDATE session_observation_contexts SET valid_until=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().plusSeconds(5)),
        Long.parseLong(item.observationContextId()));
    var observed = Instant.now();
    heartbeat.heartbeat(f.principal(), f.device(), f.sessionString(),
        new SessionDtos.HeartbeatRequest(observed, 100L, item.observationContextId()));
    Instant until = contextRows.findById(Long.parseLong(item.observationContextId()))
        .orElseThrow().getValidUntil();
    assertTrue(until.isAfter(observed.plusSeconds(25)));
    heartbeat.heartbeat(f.principal(), f.device(), f.sessionString(),
        new SessionDtos.HeartbeatRequest(observed, 100L, item.observationContextId()));
    assertEquals(until, contextRows.findById(Long.parseLong(item.observationContextId()))
        .orElseThrow().getValidUntil());
    assertThrows(ApiException.class, () -> heartbeat.heartbeat(f.principal(), f.device(),
        f.sessionString(), new SessionDtos.HeartbeatRequest(Instant.now().plusSeconds(90),
            101L, item.observationContextId())));
    assertEquals(until, contextRows.findById(Long.parseLong(item.observationContextId()))
        .orElseThrow().getValidUntil());
  }

  @Test
  void oldAnalysisCompletionCannotOverrideNewNoneContext() {
    var f = fixture();
    var item = contexts.put(f.principal(), f.device(), f.sessionString(),
        contextRequest(1, Observer.DESKTOP));
    var input = analysis(item.observationContextId(), 1);
    var prepared = store.prepare(f.principal(), f.session(), input);
    contexts.put(f.principal(), f.device(), f.sessionString(), contextRequest(2, Observer.NONE));
    store.complete(f.principal(), prepared, input, unrelated());
    evaluator.evaluate(f.principal().userId(), f.session());
    assertTrue(events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).isEmpty());
    var snapshot = json.readValue(states.findById(f.session()).orElseThrow().getTimelineJson(),
        OffTaskTimeline.Snapshot.class);
    assertEquals(null, snapshot.openEpisode());
    assertThrows(ApiException.class, () -> store.prepare(f.principal(), f.session(),
        analysis(item.observationContextId(), 2)));
  }

  @Test
  void liveContextAndHeartbeatProduceEventThenSentReceiptStopsRedisplay() throws Exception {
    var f = fixture();
    var event = generate(f);
    mvc.perform(auth(get(interventionsPath(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].observationContextId").isString())
        .andExpect(jsonPath("$.data[0].remainingTtlMs").isNumber());
    String nonce = UUID.randomUUID().toString();
    var sent = delivery(nonce, InterventionDtos.DispatchStatus.SENT);
    var response = mvc.perform(auth(post(deliveryPath(event)), f)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(sent)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.dispatchStatus").value("SENT"))
        .andExpect(jsonPath("$.data.cooldownUntil").isString()).andReturn();
    var receipt = deliveries.receive(f.principal(), f.device(), Long.toString(event), sent);
    assertEquals(json.readTree(response.getResponse().getContentAsString())
        .path("data").path("acceptedAt").asString(), receipt.acceptedAt().toString());
    assertEquals(receipt.acceptedAt().plusSeconds(300), receipt.cooldownUntil());
    mvc.perform(auth(get(interventionsPath(f)), f)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    var duplicate = deliveries.receive(f.principal(), f.device(), Long.toString(event),
        delivery(UUID.randomUUID().toString(), InterventionDtos.DispatchStatus.SENT));
    assertEquals(receipt.cooldownUntil(), duplicate.cooldownUntil());
    jdbc.update("UPDATE intervention_events SET expires_at=created_at WHERE id=?", event);
    var ack = interventions.acknowledge(f.principal(), f.device(), Long.toString(event),
        new InterventionDtos.Acknowledge(InterventionDtos.Action.ACKNOWLEDGED));
    assertEquals(InterventionDtos.Status.ACKNOWLEDGED, ack.status());
    assertNotNull(ack.actedAt());
    assertEquals(receipt, deliveries.receive(f.principal(), f.device(),
        Long.toString(event), sent));
  }

  @Test
  void unboundCompatibilityHistoryDoesNotChangeCurrentContextOrPending() {
    var f = fixture();
    final long event = generate(f);
    var before = states.findById(f.session()).orElseThrow().getTimelineJson();
    var input = analysis(null, 2);
    var prepared = store.prepare(f.principal(), f.session(), input);
    store.complete(f.principal(), prepared, input, unrelated());
    evaluator.evaluate(f.principal().userId(), f.session());
    assertEquals(before, states.findById(f.session()).orElseThrow().getTimelineJson());
    assertEquals(InterventionDtos.Status.PENDING, events.findById(event).orElseThrow().getStatus());
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
  }

  @Test
  void firstLateSentDoesNotTurnElapsedPendingBackIntoLiveNotification() {
    var f = fixture();
    final long event = generate(f);
    jdbc.update("UPDATE intervention_events SET expires_at=created_at WHERE id=?", event);
    deliveries.receive(f.principal(), f.device(), Long.toString(event),
        delivery(UUID.randomUUID().toString(), InterventionDtos.DispatchStatus.SENT));
    var saved = events.findById(event).orElseThrow();
    assertEquals(InterventionDtos.Status.EXPIRED, saved.getStatus());
    assertNotNull(saved.getDeliveryConfirmedAt());
    assertEquals(null, saved.getActedAt());
  }

  @Test
  void actualHttpContextAndDeliveryUseTraceAndPublicEnvelope() throws Exception {
    var f = fixture();
    var response = http(f, "PUT", contextPath(f),
        json.writeValueAsString(contextRequest(1, Observer.DESKTOP)));
    assertEquals(200, response.statusCode());
    assertTrue(response.headers().firstValue("X-Trace-Id").isPresent());
    String contextId = json.readTree(response.body()).path("data")
        .path("observationContextId").asString();
    var current = http(f, "GET", contextPath(f), null);
    assertEquals(200, current.statusCode());
    assertEquals(contextId, json.readTree(current.body()).path("data")
        .path("observationContextId").asString());
    long event = generateWithContext(f, contextId);
    var pending = http(f, "GET", interventionsPath(f), null);
    assertEquals(200, pending.statusCode());
    assertEquals(Long.toString(event), json.readTree(pending.body()).path("data").get(0)
        .path("interventionId").asString());
    var sent = http(f, "POST", deliveryPath(event), json.writeValueAsString(
        delivery(UUID.randomUUID().toString(), InterventionDtos.DispatchStatus.SENT)));
    assertEquals(200, sent.statusCode());
    assertEquals("SENT", json.readTree(sent.body()).path("data").path("dispatchStatus").asString());
    assertEquals(0, json.readTree(http(f, "GET", interventionsPath(f), null).body())
        .path("data").size());
  }

  @Test
  void notSentCanRetryButUnknownSurvivesLaterNotSent() {
    var f = fixture();
    final long event = generate(f);
    var notSent = delivery(UUID.randomUUID().toString(), InterventionDtos.DispatchStatus.NOT_SENT);
    deliveries.receive(f.principal(), f.device(), Long.toString(event), notSent);
    tx.executeWithoutResult(ignored -> assertTrue(foreground.definitelyNotSent(event)));
    assertEquals(1, interventions.list(f.principal(), f.sessionString()).size());
    assertThrows(ApiException.class, () -> deliveries.receive(f.principal(), f.device(),
        Long.toString(event),
        delivery(notSent.clientDeliveryId(), InterventionDtos.DispatchStatus.SENT)));
    deliveries.receive(f.principal(), f.device(), Long.toString(event),
        delivery(UUID.randomUUID().toString(), InterventionDtos.DispatchStatus.UNKNOWN));
    deliveries.receive(f.principal(), f.device(), Long.toString(event),
        delivery(UUID.randomUUID().toString(), InterventionDtos.DispatchStatus.NOT_SENT));
    tx.executeWithoutResult(ignored -> assertFalse(foreground.definitelyNotSent(event)));
    jdbc.update("UPDATE intervention_events SET expires_at=created_at WHERE id=?", event);
    evaluator.evaluate(f.principal().userId(), f.session());
    assertEquals(1, events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).size());
  }

  @Test
  void receiptValidationOwnershipAndLateSentPreserveExpiredState() throws Exception {
    var f = fixture();
    var other = fixture();
    final long event = generate(f);
    mvc.perform(auth(post(deliveryPath(event)), other).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(delivery(UUID.randomUUID().toString(),
            InterventionDtos.DispatchStatus.SENT)))).andExpect(status().isNotFound());
    for (String body : List.of(
        "{\"clientDeliveryId\":\"" + UUID.randomUUID()
            + "\",\"dispatchStatus\":\"SENT\",\"channels\":[]}",
        "{\"clientDeliveryId\":\"" + UUID.randomUUID()
            + "\",\"dispatchStatus\":\"SENT\",\"channels\":[\"MACOS_NOTIFICATION\"]}",
        "{\"clientDeliveryId\":\"" + UUID.randomUUID()
            + "\",\"dispatchStatus\":\"UNKNOWN\",\"channels\":[\"MOLE_UI\"]}")) {
      mvc.perform(auth(post(deliveryPath(event)), f).contentType(MediaType.APPLICATION_JSON)
          .content(body)).andExpect(status().isBadRequest());
    }
    jdbc.update("UPDATE intervention_events SET expires_at=created_at WHERE id=?", event);
    interventions.acknowledge(f.principal(), f.device(), Long.toString(event),
        new InterventionDtos.Acknowledge(InterventionDtos.Action.DISMISSED));
    deliveries.receive(f.principal(), f.device(), Long.toString(event),
        delivery(UUID.randomUUID().toString(), InterventionDtos.DispatchStatus.SENT));
    var saved = events.findById(event).orElseThrow();
    assertEquals(InterventionDtos.Status.EXPIRED, saved.getStatus());
    assertEquals(null, saved.getActedAt());
    assertNotNull(saved.getDeliveryConfirmedAt());
  }

  @Test
  void concurrentContextAndReceiptRetriesHaveOneIdentityAndCooldown() throws Exception {
    var f = fixture();
    var input = contextRequest(1, Observer.DESKTOP);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> contexts.put(f.principal(), f.device(), f.sessionString(),
          input));
      var second = executor.submit(() -> contexts.put(f.principal(), f.device(), f.sessionString(),
          input));
      assertEquals(first.get().observationContextId(), second.get().observationContextId());
    }
    long event = generateWithContext(f, contexts.get(f.principal(), f.device(), f.sessionString())
        .orElseThrow().observationContextId());
    var inputDelivery = delivery(UUID.randomUUID().toString(),
        InterventionDtos.DispatchStatus.SENT);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> deliveries.receive(f.principal(), f.device(),
          Long.toString(event), inputDelivery));
      var second = executor.submit(() -> deliveries.receive(f.principal(), f.device(),
          Long.toString(event), inputDelivery));
      assertEquals(first.get(), second.get());
    }
  }

  @Test
  void leaseExpiryClosesSpanAtExpiryNotAtLateHeartbeat() {
    var f = fixture();
    var item = contexts.put(f.principal(), f.device(), f.sessionString(),
        contextRequest(1, Observer.DESKTOP));
    final long event = generateWithContext(f, item.observationContextId());
    Instant expired = Instant.now().minusSeconds(20)
        .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    jdbc.update("UPDATE session_observation_contexts SET valid_until=? WHERE id=?",
        java.sql.Timestamp.from(expired),
        Long.parseLong(item.observationContextId()));
    evaluator.evaluate(f.principal().userId(), f.session());
    var snapshot = json.readValue(states.findById(f.session()).orElseThrow().getTimelineJson(),
        OffTaskTimeline.Snapshot.class);
    assertEquals(null, snapshot.openEpisode());
    assertEquals(expired.truncatedTo(java.time.temporal.ChronoUnit.MICROS),
        snapshot.spans().getLast().end());
    assertEquals(InterventionDtos.Status.EXPIRED, events.findById(event).orElseThrow().getStatus());
  }

  private long generate(Fixture f) {
    return generateWithContext(f, contexts.put(f.principal(), f.device(), f.sessionString(),
        contextRequest(1, Observer.DESKTOP)).observationContextId());
  }

  private long generateWithContext(Fixture f, String contextId) {
    // 시간 경과를 합성한다. 외부 AI 없이 실제 context 소속·정책·heartbeat·이벤트를 연결한다.
    Instant at = Instant.now().minusSeconds(70);
    jdbc.update("UPDATE session_observation_contexts SET created_at=?,valid_until=? WHERE id=?",
        java.sql.Timestamp.from(at.minusSeconds(1)),
        java.sql.Timestamp.from(Instant.now().plusSeconds(30)), Long.parseLong(contextId));
    tx.executeWithoutResult(ignored -> {
      users.findByIdForUpdate(f.principal().userId()).orElseThrow();
      var session = sessions.findOwnedForUpdate(f.session(), f.principal().userId()).orElseThrow();
      var run = AnalysisRunEntity.reserve(session,
          devices.getReferenceById(f.principal().deviceId()),
          analysis(contextId, 1), at);
      run.bindContext(Long.parseLong(contextId));
      runs.saveAndFlush(run);
      policy.prepared(session, run, true, false, at);
      run.complete(unrelated());
      policy.completed(session, run, at);
    });
    heartbeat.heartbeat(f.principal(), f.device(), f.sessionString(),
        new SessionDtos.HeartbeatRequest(Instant.now(), 100L, contextId));
    return events.findBySessionIdOrderByCreatedAtAscIdAsc(f.session()).getFirst().getId();
  }

  private AnalysisDtos.Request analysis(String contextId, int sequence) {
    return new AnalysisDtos.Request(UUID.randomUUID().toString(),
        AnalysisDtos.ContentSource.DESKTOP_APP_OCR, Instant.now(), BigDecimal.valueOf(sequence),
        null, null, null, BigDecimal.valueOf(sequence), contextId);
  }

  private AnalysisDecision unrelated() {
    return new AnalysisDecision(AnalysisDtos.Relation.UNRELATED, .9,
        AnalysisDtos.ReasonCode.SEMANTIC_GOAL_MISMATCH, AnalysisDtos.AnalysisStatus.COMPLETED,
        "synthetic-test", "synthetic-test");
  }

  private ObservationContextDtos.Put contextRequest(long sequence, Observer observer) {
    return new ObservationContextDtos.Put(UUID.randomUUID().toString(),
        BigDecimal.valueOf(sequence), observer);
  }

  private InterventionDtos.Delivery delivery(String nonce, InterventionDtos.DispatchStatus status) {
    return new InterventionDtos.Delivery(nonce, status,
        status == InterventionDtos.DispatchStatus.SENT
        ? List.of(SessionDtos.NotificationChannel.MOLE_UI) : List.of());
  }

  private Fixture fixture() {
    var account = login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), "test",
        "http://127.0.0.1:49152", new DesktopDevice(UUID.randomUUID().toString(), "QA", "MACOS")));
    return tx.execute(ignored -> {
      var user = users.findById(Long.parseLong(account.user().userId())).orElseThrow();
      var device = devices.findById(Long.parseLong(account.device().deviceId())).orElseThrow();
      var family = families.findByDeviceAndStatus(device.getId(), TokenFamilyEntity.Status.ACTIVE)
          .orElseThrow();
      for (var type : List.of(ConsentType.TERMS_OF_SERVICE, ConsentType.PRIVACY_POLICY,
          ConsentType.SCREEN_ANALYSIS)) {
        consents.save(UserConsentEntity.create(user, type, true, "2026-09-17", "QA",
            Instant.now()));
      }
      var goal = goals.save(GoalEntity.create(user, "합성 목표",
          GoalEntity.ConfirmationSource.MANUAL_CONFIRMED, Instant.now()));
      var session = sessions.saveAndFlush(LearningSessionEntity.create(user, goal, device, 1800000,
          "Asia/Seoul", LocalDate.now(), "{\"interventionLevel\":\"NORMAL\","
              + "\"notificationChannels\":[\"MOLE_UI\"],\"cameraAnalysisEnabled\":false}",
          "v1", Instant.now().minusSeconds(150)));
      return new Fixture(account, new FocusOnPrincipal(user.getId(), device.getId(), family.getId(),
          device.getDeviceType()), session.getId());
    });
  }

  private java.net.http.HttpResponse<String> http(Fixture f, String method, String path,
      String body) throws Exception {
    String trace = UUID.randomUUID().toString();
    var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
        "http://127.0.0.1:" + port + path))
        .header("Authorization", "Bearer " + f.account().accessToken())
        .header("X-Device-Id", f.device()).header("X-Trace-Id", trace)
        .header("Content-Type", "application/json")
        .method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
            : java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
    var response = java.net.http.HttpClient.newHttpClient()
        .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(trace, response.headers().firstValue("X-Trace-Id").orElseThrow());
    return response;
  }

  private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, Fixture f) {
    return request.header("Authorization", "Bearer " + f.account().accessToken())
        .header("X-Device-Id", f.device());
  }

  private String contextPath(Fixture f) {
    return "/api/v1/sessions/" + f.session() + "/observation-context";
  }

  private String interventionsPath(Fixture f) {
    return "/api/v1/sessions/" + f.session() + "/interventions";
  }

  private String deliveryPath(long id) {
    return "/api/v1/interventions/" + id + "/deliveries";
  }

  private record Fixture(LoginResponse account, FocusOnPrincipal principal, long session) {
    String device() {
      return Long.toString(principal.deviceId());
    }

    String sessionString() {
      return Long.toString(session);
    }
  }
}
