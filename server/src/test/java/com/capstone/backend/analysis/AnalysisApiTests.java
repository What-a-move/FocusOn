package com.capstone.backend.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.ContentPayload;
import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.Metadata;
import com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.persistence.AnalysisRunRepository;
import com.capstone.backend.analysis.service.AnalysisAiClient;
import com.capstone.backend.analysis.service.AnalysisDecision;
import com.capstone.backend.analysis.service.AnalysisRunStore;
import com.capstone.backend.analysis.service.AnalysisService;
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
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import com.capstone.backend.user.persistence.AnalysisExcludedDomainEntity;
import com.capstone.backend.user.persistence.AnalysisExcludedDomainRepository;
import com.capstone.backend.user.persistence.UserConsentEntity;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserConsentRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** 공개 HTTP·실제 저장·지연 결과 경쟁을 함께 검증한다. PG에서도 같은 테스트를 실행한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnalysisApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private TransactionTemplate tx;
  @Autowired private UserRepository users;
  @Autowired private UserDeviceRepository devices;
  @Autowired private TokenFamilyRepository families;
  @Autowired private GoalRepository goals;
  @Autowired private LearningSessionRepository sessions;
  @Autowired private UserConsentRepository consents;
  @Autowired private AnalysisExcludedDomainRepository exclusions;
  @Autowired private AnalysisRunRepository runs;
  @Autowired private AnalysisRunStore store;
  @Autowired private AnalysisService service;
  @Autowired private PairingService pairing;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private AnalysisAiClient ai;
  @LocalServerPort private int port;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "analysis-" + UUID.randomUUID());
    when(ai.analyze(any(), any(), anyLong())).thenReturn(related());
  }

  @Test
  void actualHttpReturnsPublicShapeAndServerPolicy() throws Exception {
    Fixture f = fixture();
    String trace = UUID.randomUUID().toString();
    var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
        "http://127.0.0.1:" + port + path(f.sessionId())))
        .header("Authorization",
            "Bearer " + f.login().accessToken())
        .header("X-Device-Id", f.login().device().deviceId()).header("X-Trace-Id", trace)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request(null)))).build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertEquals(trace, response.headers().firstValue("X-Trace-Id").orElseThrow());
    var result = json.readTree(response.body()).path("data");
    assertEquals("RELATED", result.path("relation").asString());
    assertEquals("COMPLETED", result.path("analysisStatus").asString());
    assertEquals("v1", result.path("policyVersion").asString());
    assertEquals("SEMANTIC_GOAL_MATCH", result.path("reasonCode").asString());
    assertFalse(response.body().contains("private synthetic body"));
  }

  @Test
  void guardDomainAndConsentSkipAiWithoutSavingInput() throws Exception {
    Fixture f = fixture();
    mvc.perform(call(f, request(GuardStatus.EXCLUDED))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("EXCLUDED"))
        .andExpect(jsonPath("$.data.analysisStatus").value("SKIPPED"));
    tx.executeWithoutResult(ignored -> exclusions.save(AnalysisExcludedDomainEntity.create(
        users.getReferenceById(f.principal().userId()), "example.com", Instant.now())));
    mvc.perform(call(f, request(null))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("EXCLUDED"));
    tx.executeWithoutResult(ignored -> consents.findByUserIdAndConsentType(f.principal().userId(),
        ConsentType.SCREEN_ANALYSIS).orElseThrow().apply(false, "2026-09-17", "SETTINGS",
            Instant.now()));
    mvc.perform(call(f, request(null))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("PRIVACY_BLOCKED"));
    verify(ai, never()).analyze(any(), any(), anyLong());
    assertTrue(runs.findFirstBySessionIdAndDeviceIdOrderByObservedAtDescIdDesc(
        f.sessionId(), f.principal().deviceId()).orElseThrow().getModelVersion() == null);
  }

  @Test
  void sensitiveTextIsBlockedBeforeExternalCall() throws Exception {
    Fixture f = fixture();
    var r = request(null);
    var sensitive = new Request(r.clientRunId(), r.contentSource(), r.observedAt(),
        BigDecimal.ONE, null,
        r.metadata(), new ContentPayload("Bearer synthetic-secret-token-value"));
    mvc.perform(call(f, sensitive)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("PRIVACY_BLOCKED"));
    verify(ai, never()).analyze(any(), any(), anyLong());
  }

  @Test
  void duplicateRateLimitAndMissingHeadersUseCommonErrors() throws Exception {
    Fixture f = fixture();
    Request input = request(GuardStatus.EXCLUDED);
    mvc.perform(call(f, input)).andExpect(status().isOk());
    mvc.perform(call(f, input)).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    for (int i = 0; i < 6; i++) {
      mvc.perform(call(f, request(GuardStatus.EXCLUDED))).andExpect(status().isOk());
    }
    mvc.perform(call(f, request(GuardStatus.EXCLUDED))).andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    mvc.perform(post(path(f.sessionId())).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(input))).andExpect(status().isUnauthorized());
    mvc.perform(post(path(f.sessionId())).header("Authorization",
            "Bearer " + f.login().accessToken())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void foreignUserAndAnotherDesktopCannotSubmit() throws Exception {
    Fixture owner = fixture();
    Fixture other = fixture();
    mvc.perform(post(path(owner.sessionId())).header("Authorization",
            "Bearer " + other.login().accessToken())
        .header("X-Device-Id", other.login().device().deviceId())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(null))))
        .andExpect(status().isNotFound());
    tx.executeWithoutResult(ignored -> sessions.findById(owner.sessionId()).orElseThrow()
        .pause(SessionStatus.PAUSED, "USER", 1000));
    mvc.perform(call(owner, request(null))).andExpect(status().isConflict());
  }

  @Test
  void malformedBodyAndGuardContentAreRejected() throws Exception {
    Fixture f = fixture();
    Request input = request(null);
    String valid = json.writeValueAsString(input);
    for (String body : new String[] {
        valid.replace(input.clientRunId(), "not-a-uuid"),
        valid.replace("CHROME_DOM", "CHROME_VIEWPORT_OCR"),
        valid.replace("CHROME_DOM", "CAMERA_LOCAL"),
        valid.replace("\"guardStatus\":null", "\"guardStatus\":\"EXCLUDED\""),
        valid.replace("example.com", "https://example.com/private?token=hidden"),
        valid.replace("private synthetic body", "x".repeat(2001)),
        valid.replace("\"sequence\":1", "\"sequence\":0"),
        valid.replace("\"sequence\":1", "\"sequence\":1.2"),
        valid.replace("\"sequence\":1", "\"sequence\":9223372036854775808")}) {
      mvc.perform(post(path(f.sessionId())).header("Authorization",
            "Bearer " + f.login().accessToken())
          .header("X-Device-Id", f.login().device().deviceId())
          .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void chromeDomBodyRequiresHostBeforeStorageOrAi() throws Exception {
    Fixture f = fixture();
    var source = request(null);
    long before = runs.count();
    for (Metadata metadata : new Metadata[] {null, new Metadata("Chrome", null, "test", null),
        new Metadata("Chrome", null, "test", "")}) {
      var missingHost = new Request(UUID.randomUUID().toString(), ContentSource.CHROME_DOM,
          source.observedAt(), BigDecimal.ONE, null, metadata, source.contentPayload());
      mvc.perform(call(f, missingHost)).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    assertEquals(before, runs.count());
    verify(ai, never()).analyze(any(), any(), anyLong());
    mvc.perform(call(f, request(GuardStatus.EXCLUDED))).andExpect(status().isOk());
    mvc.perform(call(f, request(GuardStatus.PRIVACY_BLOCKED))).andExpect(status().isOk());
  }

  @Test
  void oldAiCompletionCannotOverwriteLatestExcludedAcrossSources() throws Exception {
    Fixture f = fixture();
    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    when(ai.analyze(any(), any(), anyLong())).thenAnswer(invocation -> {
      started.countDown();
      assertTrue(finish.await(10, TimeUnit.SECONDS));
      return related();
    });
    Request first = request(null);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var pending = executor.submit(() -> service.analyze(f.principal(),
          f.login().device().deviceId(), Long.toString(f.sessionId()), first));
      assertTrue(started.await(10, TimeUnit.SECONDS));
      try {
        var guard = new Request(UUID.randomUUID().toString(), ContentSource.APP_META,
            first.observedAt().plusMillis(1), BigDecimal.ONE, GuardStatus.EXCLUDED, null, null);
        assertEquals(Relation.EXCLUDED, service.analyze(f.principal(),
            f.login().device().deviceId(), Long.toString(f.sessionId()), guard).relation());
      } finally {
        finish.countDown();
      }
      assertEquals(Relation.RELATED, pending.get(10, TimeUnit.SECONDS).relation());
    }
    var latest = runs.findFirstBySessionIdAndDeviceIdOrderByObservedAtDescIdDesc(
        f.sessionId(), f.principal().deviceId()).orElseThrow();
    assertEquals(Relation.EXCLUDED, latest.effectiveRelation());
  }

  @Test
  void sessionChangeAndConsentWithdrawalDuringAiCannotProduceWarning() {
    Fixture f = fixture();
    Request r = request(null);
    var prepared = store.prepare(f.principal(), f.sessionId(), r);
    tx.executeWithoutResult(ignored -> sessions.findById(f.sessionId()).orElseThrow()
        .pause(SessionStatus.PAUSED, "USER", 1000));
    assertEquals(Relation.UNCERTAIN,
        store.complete(f.principal(), prepared, r, related()).relation());
    tx.executeWithoutResult(ignored -> consents.findByUserIdAndConsentType(f.principal().userId(),
        ConsentType.SCREEN_ANALYSIS).orElseThrow().apply(false, "2026-09-17", "SETTINGS",
            Instant.now()));
    assertEquals(Relation.PRIVACY_BLOCKED,
        store.complete(f.principal(), prepared, r, related()).relation());
  }

  @Test
  void metadataUnrelatedIsNotAnOffTaskInterval() {
    Fixture f = fixture();
    when(ai.analyze(any(), any(), anyLong())).thenReturn(new AnalysisDecision(Relation.UNRELATED,
        null, ReasonCode.SEMANTIC_GOAL_MISMATCH, AnalysisStatus.COMPLETED,
        "test", "v1-uncalibrated"));
    var request = new Request(UUID.randomUUID().toString(), ContentSource.TAB_META,
        Instant.now().minusSeconds(1), BigDecimal.ONE, null,
        new Metadata("Chrome", null, "test", ""), null);
    assertEquals(Relation.UNRELATED, service.analyze(f.principal(),
        f.login().device().deviceId(),
        Long.toString(f.sessionId()), request).relation());
    assertEquals(Relation.UNCERTAIN,
        runs.findFirstBySessionIdAndDeviceIdOrderByObservedAtDescIdDesc(
        f.sessionId(), f.principal().deviceId()).orElseThrow().effectiveRelation());
  }

  @Test
  void linkedExtensionCanSubmitButRevocationDuringAiRejectsCompletion() throws Exception {
    Fixture f = fixture();
    var linked = pairing.claim(new ClaimRequest(pairing.issue(f.principal()).codeDisplay(),
        "analysis-extension-" + UUID.randomUUID()), "127.0.0.171");
    String deviceId = linked.device().deviceId();
    mvc.perform(post(path(f.sessionId())).header("Authorization", "Bearer " + linked.accessToken())
        .header("X-Device-Id", deviceId).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(request(null))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.relation").value("RELATED"));
    var principal = tx.execute(ignored -> {
      var family = families.findByDeviceAndStatus(Long.parseLong(deviceId),
          TokenFamilyEntity.Status.ACTIVE).orElseThrow();
      return new FocusOnPrincipal(f.principal().userId(), Long.parseLong(deviceId), family.getId(),
          com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType.CHROME_EXTENSION);
    });
    var request = request(null);
    var prepared = store.prepare(principal, f.sessionId(), request);
    tx.executeWithoutResult(ignored -> {
      var family = families.findById(principal.tokenFamilyId()).orElseThrow();
      family.setStatus(TokenFamilyEntity.Status.REVOKED);
      family.setRevokedReason(TokenFamilyEntity.RevokedReason.UNLINK);
      family.setRevokedAt(Instant.now());
    });
    assertEquals("LINK_TOKEN_REVOKED", assertThrows(ApiException.class,
        () -> store.complete(principal, prepared, request, related())).code().name());
  }

  @Test
  void aiUnavailableStillReturnsSuccessfulUncertainRecord() throws Exception {
    Fixture f = fixture();
    when(ai.analyze(any(), any(), anyLong())).thenReturn(AnalysisDecision.unavailable());
    mvc.perform(call(f, request(null))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("UNCERTAIN"))
        .andExpect(jsonPath("$.data.analysisStatus").value("ERROR"))
        .andExpect(jsonPath("$.data.reasonCode").value("ANALYSIS_UNAVAILABLE"));
  }

  @Test
  void concurrentDuplicateRunsCreateExactlyOneHistoryRow() throws Exception {
    Fixture f = fixture();
    var input = request(GuardStatus.EXCLUDED);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return mvc.perform(call(f, input)).andReturn().getResponse().getStatus();
      })).toList();
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      var statuses = new java.util.ArrayList<Integer>();
      for (var task : tasks) {
        statuses.add(task.get(10, TimeUnit.SECONDS));
      }
      statuses.sort(Integer::compareTo);
      assertEquals(java.util.List.of(200, 409), statuses);
    }
  }

  @Test
  void heartbeatVersionUpdateDoesNotDiscardSameRunningInterval() {
    Fixture f = fixture();
    Request input = request(null);
    var prepared = store.prepare(f.principal(), f.sessionId(), input);
    jdbc.update("UPDATE learning_sessions SET version = version + 1 WHERE id = ?", f.sessionId());
    assertEquals(Relation.RELATED,
        store.complete(f.principal(), prepared, input, related()).relation());
  }

  @Test
  void sameUserAnotherDesktopCannotPretendToBeRunner() {
    Fixture f = fixture();
    var otherPrincipal = tx.execute(ignored -> {
      var user = users.getReferenceById(f.principal().userId());
      var device = new com.capstone.backend.auth.persistence.UserDeviceEntity();
      device.setUser(user);
      device.setDeviceType(f.principal().deviceType());
      device.setInstallationIdHash(com.capstone.backend.auth.service.TokenSecrets.hash(
          UUID.randomUUID().toString()));
      device.setDisplayName("QA Other Desktop");
      devices.saveAndFlush(device);
      var family = new TokenFamilyEntity();
      family.setUser(user);
      family.setDevice(device);
      family.setExpiresAt(Instant.now().plusSeconds(3600));
      families.saveAndFlush(family);
      return new FocusOnPrincipal(user.getId(), device.getId(), family.getId(),
          device.getDeviceType());
    });
    assertEquals("SESSION_NOT_FOUND", assertThrows(ApiException.class,
        () -> store.prepare(otherPrincipal, f.sessionId(), request(null))).code().name());
  }

  private Fixture fixture() {
    var account = login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), "test",
        "http://127.0.0.1:49152", new DesktopDevice(UUID.randomUUID().toString(), "QA", "MACOS")));
    return tx.execute(ignored -> {
      var user = users.findById(Long.parseLong(account.user().userId())).orElseThrow();
      var device = devices.findById(Long.parseLong(account.device().deviceId())).orElseThrow();
      var family = families.findByDeviceAndStatus(device.getId(), TokenFamilyEntity.Status.ACTIVE)
          .orElseThrow();
      for (var type : new ConsentType[] {ConsentType.TERMS_OF_SERVICE, ConsentType.PRIVACY_POLICY,
          ConsentType.SCREEN_ANALYSIS}) {
        consents.save(UserConsentEntity.create(user, type, true, "2026-09-17", "ONBOARDING",
            Instant.now()));
      }
      var goal = goals.save(GoalEntity.create(user, "테스트 목표",
          GoalEntity.ConfirmationSource.MANUAL_CONFIRMED, Instant.now()));
      var session = sessions.saveAndFlush(LearningSessionEntity.create(user, goal, device,
          1800000, "Asia/Seoul", LocalDate.now(), "{}", "v1", Instant.now().minusSeconds(120)));
      return new Fixture(account, new FocusOnPrincipal(user.getId(), device.getId(), family.getId(),
          device.getDeviceType()), session.getId());
    });
  }

  private MockHttpServletRequestBuilder call(Fixture f, Request request) {
    return post(path(f.sessionId())).header("Authorization",
            "Bearer " + f.login().accessToken())
        .header("X-Device-Id", f.login().device().deviceId())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(request));
  }

  private static String path(long id) {
    return "/api/v1/sessions/" + id + "/analysis-runs";
  }

  private static Request request(GuardStatus guard) {
    return new Request(UUID.randomUUID().toString(), ContentSource.CHROME_DOM,
        Instant.now().minusSeconds(1), BigDecimal.ONE, guard,
        guard == null ? new Metadata("Chrome", null, "synthetic title", "example.com") : null,
        guard == null ? new ContentPayload("private synthetic body") : null);
  }

  private static AnalysisDecision related() {
    return new AnalysisDecision(Relation.RELATED, .9, ReasonCode.SEMANTIC_GOAL_MATCH,
        AnalysisStatus.COMPLETED, "test-model", "test-prompt");
  }

  private record Fixture(LoginResponse login, FocusOnPrincipal principal, long sessionId) {}
}
