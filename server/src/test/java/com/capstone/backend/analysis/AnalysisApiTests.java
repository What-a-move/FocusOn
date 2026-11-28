package com.capstone.backend.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import com.capstone.backend.analysis.service.AnalysisRunRetention;
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
import com.capstone.backend.common.api.SafeApiLog;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.goal.persistence.GoalRepository;
import com.capstone.backend.intervention.dto.InterventionDtos;
import com.capstone.backend.intervention.persistence.InterventionDeliveryEntity;
import com.capstone.backend.intervention.persistence.InterventionDeliveryRepository;
import com.capstone.backend.intervention.persistence.InterventionEventEntity;
import com.capstone.backend.intervention.persistence.InterventionEventRepository;
import com.capstone.backend.report.persistence.LearningReviewEvidenceEntity;
import com.capstone.backend.report.persistence.LearningReviewEvidenceRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import com.capstone.backend.user.persistence.AnalysisExcludedDomainEntity;
import com.capstone.backend.user.persistence.AnalysisExcludedDomainRepository;
import com.capstone.backend.user.persistence.UserConsentEntity;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserConsentRepository;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.jpa.open-in-view=true")
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
  @Autowired private AnalysisRunRetention retention;
  @Autowired private AnalysisRunStore store;
  @Autowired private AnalysisService service;
  @Autowired private PairingService pairing;
  @Autowired private InterventionEventRepository interventionEvents;
  @Autowired private InterventionDeliveryRepository interventionDeliveries;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired private LearningReviewEvidenceRepository evidences;
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

  @ParameterizedTest
  @CsvSource(value = {"200,false,RELATED,COMPLETED,SEMANTIC_GOAL_MATCH",
      "503,false,UNCERTAIN,ERROR,ANALYSIS_UNAVAILABLE",
      "200,true,PRIVACY_BLOCKED,SKIPPED,NONE"}, nullValues = "NONE")
  void actualHttpTracesAnalysisThroughFakeAi(int aiStatus, boolean withdrawConsent,
      Relation relation, AnalysisStatus status, ReasonCode reasonCode) throws Exception {
    Fixture f = fixture();
    var input = request(null);
    long goalId = f.goalId();
    final String trace = UUID.randomUUID().toString();
    var aiTrace = new AtomicReference<String>();
    var fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fake.createContext("/internal/v1/analysis-runs", exchange -> {
      aiTrace.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
      byte[] body = """
          {"decision":"RELATED","confidence":0.9,"reasonCode":"SEMANTIC_GOAL_MATCH",
           "modelVersion":"test-model","promptVersion":"test-prompt",
           "matchStatus":"MATCHED","matchedGoalId":"%s",
           "reviewEvidence":[{"conceptLabel":"synthetic review concept","evidenceType":"CONCEPT"}],
           "privateExtra":"private synthetic AI response"}
          """.formatted(goalId).getBytes(StandardCharsets.UTF_8);
      try (exchange) {
        if (withdrawConsent) {
          tx.executeWithoutResult(ignored -> consents.findByUserIdAndConsentType(
                  f.principal().userId(), ConsentType.SCREEN_ANALYSIS).orElseThrow()
              .apply(false, "2026-09-17", "SETTINGS", Instant.now()));
        }
        exchange.sendResponseHeaders(aiStatus, body.length);
        exchange.getResponseBody().write(body);
      }
    });
    fake.start();
    var client = new AnalysisAiClient(json, "http://127.0.0.1:" + fake.getAddress().getPort(),
        "private-test-service-key");
    when(ai.analyze(any(), any(), anyLong())).thenAnswer(invocation -> client.analyze(
        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    var logger = (Logger) LoggerFactory.getLogger(SafeApiLog.class);
    var requestLogged = new CountDownLatch(1);
    var logs = new ListAppender<ILoggingEvent>() {
      @Override
      protected void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing();
        if (trace.equals(event.getMDCPropertyMap().get("traceId"))) {
          super.append(event);
          if ("http.out".equals(event.getMessage())) {
            requestLogged.countDown();
          }
        }
      }
    };
    logs.start();
    logger.addAppender(logs);
    try {
      var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
          "http://127.0.0.1:" + port + path(f.sessionId()) + "?debug=private-query-value"))
          .header("Authorization", "Bearer " + f.login().accessToken())
          .header("X-Device-Id", f.login().device().deviceId()).header("X-Trace-Id", trace)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());
      assertEquals(trace, response.headers().firstValue("X-Trace-Id").orElseThrow());
      assertEquals(trace, aiTrace.get());
      var result = json.readTree(response.body()).path("data");
      assertEquals(relation.name(), result.path("relation").asString());
      assertEquals(status.name(), result.path("analysisStatus").asString());
      assertEquals(reasonCode == null ? null : reasonCode.name(),
          result.path("reasonCode").asString(null));
      var confidence = result.path("confidence");
      if (relation == Relation.RELATED) {
        assertTrue(confidence.isNumber());
        assertEquals(0.9, confidence.asDouble());
        assertEquals("MATCHED", result.path("matchStatus").asString());
        assertEquals(Long.toString(goalId), result.path("matchedGoalId").asString());
      } else {
        assertTrue(confidence.isNull() || confidence.isMissingNode());
      }
      assertEquals("v1", result.path("policyVersion").asString());
      assertFalse(response.body().contains("private synthetic body"));
      assertFalse(response.body().contains("synthetic review concept"));
      assertTrue(result.path("reviewEvidence").isMissingNode());
      assertFalse(response.body().contains("private synthetic AI response"));
      assertTrue(requestLogged.await(5, TimeUnit.SECONDS));
      boolean aiSucceeded = aiStatus == 200;
      assertEquals(List.of("http.in", "gate.rule.pass", "ai.call.start",
          aiSucceeded ? "ai.call.ok" : "ai.call.fail",
          "store.write.ok", "http.out"), logs.list.stream().map(ILoggingEvent::getMessage)
          .toList());
      var aiFields = logs.list.get(3).getKeyValuePairs().stream()
          .collect(Collectors.toMap(field -> field.key, field -> field.value));
      var storedFields = logs.list.get(4).getKeyValuePairs().stream()
          .collect(Collectors.toMap(field -> field.key, field -> field.value));
      assertEquals(aiStatus, aiFields.get("upstreamStatus"));
      assertEquals(aiSucceeded ? "RELATED" : "UNCERTAIN", aiFields.get("relation"));
      assertEquals(relation.name(), storedFields.get("relation"));
      assertEquals(relation == Relation.RELATED ? 0.9 : null, storedFields.get("confidence"));
      assertEquals(reasonCode == null ? null : reasonCode.name(), storedFields.get("reasonCode"));
      var stored = runs.findById(result.path("analysisRunId").asLong()).orElseThrow().response();
      assertEquals(relation, stored.relation());
      assertEquals(status, stored.analysisStatus());
      assertEquals(reasonCode, stored.reasonCode());
      assertEquals(stored.confidence(), storedFields.get("confidence"));
      var history = mvc.perform(get(path(f.sessionId())).header("Authorization",
              "Bearer " + f.login().accessToken()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].reviewEvidence").doesNotExist())
          .andReturn().getResponse().getContentAsString();
      assertFalse(history.contains("synthetic review concept"));
      var storedEvidence = evidenceOf(f.sessionId());
      assertEquals(relation == Relation.RELATED ? List.of("synthetic review concept")
          : List.of(), storedEvidence.stream().map(LearningReviewEvidenceEntity::getConceptLabel)
          .toList());
      for (var event : logs.list) {
        var logged = event.getFormattedMessage() + event.getKeyValuePairs()
            + event.getMDCPropertyMap();
        assertFalse(logged.contains("private synthetic body"));
        assertFalse(logged.contains("synthetic title"));
        assertFalse(logged.contains("private synthetic AI response"));
        assertFalse(logged.contains("synthetic review concept"));
        assertFalse(logged.contains("private-test-service-key"));
        assertFalse(logged.contains("private-query-value"));
        assertFalse(logged.contains(f.login().accessToken()));
        assertNull(event.getThrowableProxy());
      }
    } finally {
      logger.detachAppender(logs);
      logs.stop();
      fake.stop(0);
    }
  }

  /** UNRELATED 판정만 도메인·앱 규칙의 딴짓 종류를 응답·이력·DB에 남긴다(#381). */
  @ParameterizedTest
  @CsvSource(value = {"CHROME_DOM,m.youtube.com,Chrome,UNRELATED,VIDEO",
      "CHROME_DOM,news.naver.com,Chrome,UNRELATED,NEWS",
      "CHROME_DOM,docs.example.com,Chrome,UNRELATED,OTHER",
      "DESKTOP_APP_OCR,NONE,Discord,UNRELATED,MESSENGER",
      "DESKTOP_APP_OCR,NONE,Unknown App,UNRELATED,OTHER",
      "CHROME_DOM,m.youtube.com,Chrome,RELATED,NONE",
      "CHROME_DOM,m.youtube.com,Chrome,UNCERTAIN,NONE"}, nullValues = "NONE")
  void offTaskCategoryIsSetOnlyForUnrelated(ContentSource source, String host, String app,
      Relation relation, String category) throws Exception {
    Fixture f = fixture();
    when(ai.analyze(any(), any(), anyLong())).thenReturn(new AnalysisDecision(relation,
        relation == Relation.UNCERTAIN ? null : .9, relation == Relation.RELATED
            ? ReasonCode.SEMANTIC_GOAL_MATCH : relation == Relation.UNRELATED
            ? ReasonCode.SEMANTIC_GOAL_MISMATCH : ReasonCode.AMBIGUOUS_CONTEXT,
        AnalysisStatus.COMPLETED, "test-model", "test-prompt"));
    var input = new Request(UUID.randomUUID().toString(), source, Instant.now().minusSeconds(1),
        BigDecimal.ONE, null, new Metadata(app, null, "synthetic title", host),
        new ContentPayload("private synthetic body"));
    var posted = json.readTree(mvc.perform(call(f, input)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString()).path("data");
    assertEquals(relation.name(), posted.path("relation").asString());
    assertEquals(category, posted.path("offTaskCategory").asString(null));
    assertEquals(category, jdbc.queryForObject(
        "SELECT off_task_category FROM analysis_runs WHERE session_id = ?", String.class,
        f.sessionId()));
    var history = json.readTree(mvc.perform(get(path(f.sessionId())).header("Authorization",
            "Bearer " + f.login().accessToken()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    assertEquals(category, history.get(0).path("offTaskCategory").asString(null));
    assertFalse(history.toString().contains(host == null ? app : host));
  }

  /** 확정 RELATED의 matchStatus는 응답·이력·DB에 같은 값으로 남는다(#319, DR-030). */
  @Test
  void relatedMatchStatusIsStoredAndReturned() throws Exception {
    Fixture f = fixture();
    when(ai.analyze(any(), any(), anyLong())).thenReturn(new AnalysisDecision(Relation.RELATED,
        .9, ReasonCode.SEMANTIC_GOAL_MATCH, AnalysisStatus.COMPLETED, "test-model",
        "test-prompt", com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus.FAR));
    mvc.perform(post(path(f.sessionId())).header("Authorization",
            "Bearer " + f.login().accessToken())
        .header("X-Device-Id", f.login().device().deviceId())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("RELATED"))
        .andExpect(jsonPath("$.data.matchStatus").value("FAR"));
    assertEquals("FAR", jdbc.queryForObject(
        "SELECT match_status FROM analysis_runs WHERE session_id = ?", String.class,
        f.sessionId()));
    mvc.perform(get(path(f.sessionId())).header("Authorization",
            "Bearer " + f.login().accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].matchStatus").value("FAR"));
  }

  @Test
  void attributionAcceptsOnlySnapshotIdsAndIgnoresOutdatedGoalVersion() {
    Fixture f = fixture();
    Instant now = Instant.now();
    var first = ordered(ContentSource.CHROME_DOM, 1, 1L, now, null);
    var initial = store.prepare(f.principal(), f.sessionId(), first);
    var rejected = store.complete(f.principal(), initial, first, matched(initial.goalId() + 1));
    assertEquals(Relation.RELATED, rejected.relation());
    assertEquals(com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus.NOT_ENOUGH,
        rejected.matchStatus());
    assertNull(rejected.matchedGoalId());

    var second = ordered(ContentSource.CHROME_DOM, 2, 2L, now.plusSeconds(1), null);
    var current = store.prepare(f.principal(), f.sessionId(), second);
    var kept = store.complete(f.principal(), current, second, matched(current.goalId()));
    assertEquals(Long.toString(current.goalId()), kept.matchedGoalId());

    var third = ordered(ContentSource.CHROME_DOM, 3, 3L, now.plusSeconds(2), null);
    var pending = store.prepare(f.principal(), f.sessionId(), third);
    tx.executeWithoutResult(status -> sessions.incrementGoalVersion(f.sessionId(), 1));
    var outdated = store.complete(f.principal(), pending, third, matched(current.goalId()));
    assertEquals(Relation.UNCERTAIN, outdated.relation());
    assertEquals(1, outdated.goalVersion());
    assertNull(outdated.matchedGoalId());
  }

  /** RELATED가 아닌 판정에는 matchStatus를 저장하지 않는다. */
  @Test
  void nonRelatedDecisionDropsMatchStatus() throws Exception {
    Fixture f = fixture();
    when(ai.analyze(any(), any(), anyLong())).thenReturn(new AnalysisDecision(
        Relation.UNRELATED, .9, ReasonCode.SEMANTIC_GOAL_MISMATCH, AnalysisStatus.COMPLETED,
        "test-model", "test-prompt",
        com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus.MATCHED));
    mvc.perform(post(path(f.sessionId())).header("Authorization",
            "Bearer " + f.login().accessToken())
        .header("X-Device-Id", f.login().device().deviceId())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("UNRELATED"))
        .andExpect(jsonPath("$.data.matchStatus").doesNotExist());
    assertEquals(null, jdbc.queryForObject(
        "SELECT match_status FROM analysis_runs WHERE session_id = ?", String.class,
        f.sessionId()));
  }

  @Test
  void messengerUsesCurrentSettingsWithoutSendingContentToAi() throws Exception {
    Fixture f = fixture();
    var unregistered = messenger(1, "DISCORD");
    mvc.perform(call(f, unregistered)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("PRIVACY_BLOCKED"))
        .andExpect(jsonPath("$.data.analysisStatus").value("SKIPPED"))
        .andExpect(jsonPath("$.data.offTaskCategory").doesNotExist())
        .andExpect(jsonPath("$.data.messengerId").doesNotExist())
        .andExpect(jsonPath("$.data.messengerScope").doesNotExist());
    assertEquals("OTHER", storedMessengerScope(f, 1));

    mvc.perform(put("/api/v1/users/me/focus-settings")
        .header("Authorization", "Bearer " + f.login().accessToken())
        .header("X-Device-Id", f.login().device().deviceId())
        .contentType(MediaType.APPLICATION_JSON).content("""
            {"interventionLevel":"NORMAL","notificationChannels":["MOLE_UI"],
             "breakSuggestionEnabled":true,"cameraAnalysisEnabled":false,
             "learningMessengers":["SLACK","DISCORD"]}
            """)).andExpect(status().isOk());
    mvc.perform(call(f, messenger(2, "DISCORD"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("PRIVACY_BLOCKED"));
    assertEquals("LEARNING", storedMessengerScope(f, 2));
    mvc.perform(call(f, messenger(3, "OTHER"))).andExpect(status().isOk());
    assertEquals("OTHER", storedMessengerScope(f, 3));

    tx.executeWithoutResult(ignored -> consents.findByUserIdAndConsentType(
            f.principal().userId(), ConsentType.SCREEN_ANALYSIS).orElseThrow()
        .apply(false, "2026-09-17", "SETTINGS", Instant.now()));
    mvc.perform(call(f, messenger(4, "DISCORD"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("PRIVACY_BLOCKED"));
    assertNull(storedMessengerScope(f, 4));

    var history = mvc.perform(get(path(f.sessionId()))
        .header("Authorization", "Bearer " + f.login().accessToken()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4))
        .andReturn().getResponse().getContentAsString();
    for (var item : json.readTree(history).path("data")) {
      assertEquals("PRIVACY_BLOCKED", item.path("relation").asString());
      assertTrue(item.path("messengerId").isMissingNode());
      assertTrue(item.path("messengerScope").isMissingNode());
    }
    assertFalse(history.contains("DISCORD"));
    assertTrue(evidenceOf(f.sessionId()).isEmpty());
    verify(ai, never()).analyze(any(), any(), anyLong());
  }

  @Test
  void invalidMessengerShapeNeverReachesAiOrStorage() throws Exception {
    Fixture f = fixture();
    var source = json.valueToTree(messenger(1, "DISCORD")).asObject();
    for (String invalid : List.of(
        "{\"metadata\":null}",
        "{\"metadata\":{}}",
        "{\"metadata\":{\"messengerId\":42}}",
        "{\"metadata\":{\"messengerId\":\"discord\"}}",
        "{\"metadata\":{\"messengerId\":\"LINE\"}}",
        "{\"metadata\":{\"messengerId\":\"DISCORD\",\"appName\":\"synthetic\"}}",
        "{\"metadata\":{\"messengerId\":\"DISCORD\",\"windowTitle\":\"synthetic\"}}",
        "{\"metadata\":{\"messengerId\":\"DISCORD\",\"pageTitle\":\"synthetic\"}}",
        "{\"metadata\":{\"messengerId\":\"DISCORD\",\"urlHost\":\"example.org\"}}",
        "{\"metadata\":{\"messengerId\":\"DISCORD\",\"text\":\"synthetic\"}}",
        "{\"windowTitle\":\"synthetic\"}",
        "{\"contentPayload\":{\"text\":\"synthetic\"}}",
        "{\"guardStatus\":2}",
        "{\"guardStatus\":\"PRIVACY_BLOCKED\"}",
        "{\"guardStatus\":null}")) {
      var body = source.deepCopy().setAll(json.readTree(invalid).asObject());
      mvc.perform(post(path(f.sessionId())).header("Authorization", "Bearer "
              + f.login().accessToken()).header("X-Device-Id", f.login().device().deviceId())
          .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
          .andExpect(result -> assertEquals(400, result.getResponse().getStatus(), invalid))
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    assertTrue(store.history(f.principal().userId(), f.sessionId()).isEmpty());
    verify(ai, never()).analyze(any(), any(), anyLong());
  }

  @Test
  void legacyAnalysisStillIgnoresUnknownRequestFields() throws Exception {
    Fixture f = fixture();
    var body = json.valueToTree(request(null)).asObject();
    body.put("extra", "synthetic");
    body.path("metadata").asObject().put("extra", "synthetic");
    mvc.perform(post(path(f.sessionId())).header("Authorization", "Bearer "
            + f.login().accessToken()).header("X-Device-Id", f.login().device().deviceId())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.relation").value("RELATED"));
    verify(ai, times(1)).analyze(any(), any(), anyLong());
  }

  private String storedMessengerScope(Fixture f, long sequence) {
    return jdbc.queryForObject(
        "SELECT messenger_scope FROM analysis_runs WHERE session_id = ? AND sequence = ?",
        String.class, f.sessionId(), sequence);
  }

  private static Request messenger(long sequence, String id) {
    return new Request(UUID.randomUUID().toString(), ContentSource.APP_META, Instant.now(),
        BigDecimal.valueOf(sequence), GuardStatus.MESSENGER,
        new Metadata(null, null, null, null, id), null, BigDecimal.valueOf(sequence));
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
        .POST(HttpRequest.BodyPublishers.ofString(
              json.writeValueAsString(request(null)))).build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertEquals(trace, response.headers().firstValue("X-Trace-Id").orElseThrow());
    var result = json.readTree(response.body()).path("data");
    assertEquals("RELATED", result.path("relation").asString());
    assertEquals("COMPLETED", result.path("analysisStatus").asString());
    assertEquals("v1", result.path("policyVersion").asString());
    assertEquals("SEMANTIC_GOAL_MATCH", result.path("reasonCode").asString());
    assertFalse(response.body().contains("private synthetic body"));

    var history = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
        "http://127.0.0.1:" + port + path(f.sessionId())))
        .header("Authorization", "Bearer " + f.login().accessToken()).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, history.statusCode());
    var items = json.readTree(history.body()).path("data");
    assertEquals(1, items.size());
    assertEquals(result, items.path(0));
  }

  @Test
  void historyOrdersAllSessionDevicesByObservedAtAndIdAfterEnd() throws Exception {
    Fixture f = fixture();
    var observed = Instant.parse("2026-10-23T01:00:00Z");
    var later = analyze(f, ordered(ContentSource.APP_META, 1, 1L, observed.plusSeconds(1), null));
    var earlier = analyze(f, ordered(ContentSource.APP_META, 2, 2L, observed,
        GuardStatus.EXCLUDED));
    var linked = pairing.claim(new ClaimRequest(pairing.issue(f.principal()).codeDisplay(),
        "history-" + UUID.randomUUID()), "127.0.0.172");
    var linkedResponse = mvc.perform(post(path(f.sessionId()))
        .header("Authorization", "Bearer " + linked.accessToken())
        .header("X-Device-Id", linked.device().deviceId()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(ordered(ContentSource.TAB_META, 1, 1L, observed,
            GuardStatus.PRIVACY_BLOCKED))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var sameTime = json.readTree(linkedResponse).path("data");
    // 같은 세션에서도 각 응답이 해당 분석 행에 저장된 정책 버전을 읽는지 검증한다.
    String storedPolicyVersion = "history-fixture-v2";
    assertEquals(1, jdbc.update("UPDATE analysis_runs SET policy_version = ? WHERE id = ?",
        storedPolicyVersion, Long.parseLong(earlier.analysisRunId())));
    tx.executeWithoutResult(ignored -> sessions.findById(f.sessionId()).orElseThrow()
        .end(Instant.now(), 1000));

    mvc.perform(get(path(f.sessionId()))
        .header("Authorization", "Bearer " + f.login().accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].analysisRunId").value(earlier.analysisRunId()))
        .andExpect(jsonPath("$.data[0].observedAt").value(observed.toString()))
        .andExpect(jsonPath("$.data[0].relation").value("EXCLUDED"))
        .andExpect(jsonPath("$.data[0].policyVersion").value(storedPolicyVersion))
        .andExpect(jsonPath("$.data[1].analysisRunId").value(sameTime.path("analysisRunId")
            .asString()))
        .andExpect(jsonPath("$.data[1].observedAt").value(observed.toString()))
        .andExpect(jsonPath("$.data[1].relation").value("PRIVACY_BLOCKED"))
        .andExpect(jsonPath("$.data[1].policyVersion").value("v1"))
        .andExpect(jsonPath("$.data[2].analysisRunId").value(later.analysisRunId()))
        .andExpect(jsonPath("$.data[2].relation").value("RELATED"))
        .andExpect(jsonPath("$.data[2].policyVersion").value("v1"))
        .andExpect(jsonPath("$.data[2].metadata").doesNotExist())
        .andExpect(jsonPath("$.data[2].contentPayload").doesNotExist())
        .andExpect(jsonPath("$.data[2].modelVersion").doesNotExist());
    verify(ai, times(1)).analyze(any(), any(), anyLong());
  }

  @Test
  void retentionClearsExpiredRunLinksAndKeepsInterventionAndDelivery() throws Exception {
    var owner = fixture();
    when(ai.analyze(any(), any(), anyLong())).thenReturn(new AnalysisDecision(
        Relation.UNRELATED, 0.9, ReasonCode.SEMANTIC_GOAL_MISMATCH, AnalysisStatus.COMPLETED,
        "test-model", "test-prompt"));
    var analysis = analyze(owner, request(null));
    long runId = Long.parseLong(analysis.analysisRunId());
    Instant end = Instant.now().minus(Duration.ofDays(90)).truncatedTo(ChronoUnit.MICROS);
    long eventId = tx.execute(ignored -> {
      var session = sessions.findById(owner.sessionId()).orElseThrow();
      var event = InterventionEventEntity.offTask(session.getId(), 1,
          "[\"MOLE_UI\"]", "v1", session.getLastResumedAt(), end.minusSeconds(1));
      event.bindAnalysisRun(runId);
      event.confirmDelivery(end);
      event.acknowledge(InterventionDtos.Action.ACKNOWLEDGED, end);
      interventionEvents.saveAndFlush(event);
      interventionDeliveries.save(InterventionDeliveryEntity.create(event.getId(),
          UUID.randomUUID(), InterventionDtos.DispatchStatus.SENT, "[\"MOLE_UI\"]",
          end, end.plusSeconds(300)));
      session.end(end, 1000);
      return event.getId();
    });

    retention.purgeDue(end.plus(Duration.ofDays(90)).minusSeconds(1));
    assertTrue(runs.findById(runId).isPresent());
    assertEquals(runId, interventionEvents.findById(eventId).orElseThrow().getAnalysisRunId());

    mvc.perform(get(path(owner.sessionId()))
        .header("Authorization", "Bearer " + owner.login().accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    assertTrue(runs.findById(runId).isPresent());

    retention.purgeDue(end.plus(Duration.ofDays(90)));
    retention.purgeDue(end.plus(Duration.ofDays(90)));

    assertTrue(runs.findById(runId).isEmpty());
    var event = interventionEvents.findById(eventId).orElseThrow();
    assertNull(event.getAnalysisRunId());
    assertEquals(InterventionDtos.Status.ACKNOWLEDGED, event.getStatus());
    var deliveries = interventionDeliveries.findByInterventionIdOrderByIdAsc(eventId);
    assertEquals(1, deliveries.size());
    assertEquals(InterventionDtos.DispatchStatus.SENT, deliveries.getFirst().getDispatchStatus());
    var session = sessions.findById(owner.sessionId()).orElseThrow();
    assertEquals(SessionStatus.ENDED, session.getStatus());
    mvc.perform(get(path(owner.sessionId()))
        .header("Authorization", "Bearer " + owner.login().accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void retentionRemovesAbandonedHistoryAndKeepsRecentAndActiveRuns() throws Exception {
    var abandoned = fixture();
    var recent = fixture();
    var active = fixture();
    long abandonedRun = Long.parseLong(analyze(abandoned, request(null)).analysisRunId());
    final long recentRun = Long.parseLong(analyze(recent, request(null)).analysisRunId());
    final long activeRun = Long.parseLong(analyze(active, request(null)).analysisRunId());
    Instant end = Instant.now().minus(Duration.ofDays(90)).truncatedTo(ChronoUnit.MICROS);
    tx.executeWithoutResult(ignored -> {
      sessions.findById(abandoned.sessionId()).orElseThrow().abandon(end);
      sessions.findById(recent.sessionId()).orElseThrow().end(end.plus(Duration.ofDays(1)), 1000);
    });

    mvc.perform(get(path(abandoned.sessionId()))
        .header("Authorization", "Bearer " + abandoned.login().accessToken()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    assertTrue(runs.findById(abandonedRun).isPresent());
    for (var available : List.of(recent, active)) {
      mvc.perform(get(path(available.sessionId()))
          .header("Authorization", "Bearer " + available.login().accessToken()))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    retention.purgeDue(end.plus(Duration.ofDays(90)));

    assertTrue(runs.findById(abandonedRun).isEmpty());
    assertTrue(runs.findById(recentRun).isPresent());
    assertTrue(runs.findById(activeRun).isPresent());
  }

  @Test
  void verifiedReviewEvidenceIsStoredOncePerConcept() {
    var f = fixture();
    // PostgreSQL은 나노초를 마이크로초로 반올림하므로 저장 가능한 정밀도로 맞춘다.
    Instant now = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS);
    var labels = List.of("Spring  Transaction", "spring transaction", "JPA Lock");
    for (int i = 0; i < labels.size(); i++) {
      when(ai.analyze(any(), any(), anyLong())).thenReturn(withEvidence(f.goalId(),
          labels.get(i)));
      analyze(f, ordered(ContentSource.CHROME_DOM, i + 1, (long) i + 1, now.plusSeconds(i),
          null));
    }

    var stored = evidenceOf(f.sessionId());
    assertEquals(List.of("Spring Transaction", "JPA Lock"), stored.stream()
        .map(LearningReviewEvidenceEntity::getConceptLabel).toList());
    assertEquals("CONCEPT", stored.getFirst().getEvidenceType());
    assertEquals("CHROME_DOM", stored.getFirst().getContentSource());
    assertEquals(now, stored.getFirst().getObservedAt());
  }

  @Test
  void reviewEvidenceIsDroppedWhenSessionStopsOrGoalIsNotMatched() {
    var paused = fixture();
    when(ai.analyze(any(), any(), anyLong())).thenAnswer(invocation -> {
      tx.executeWithoutResult(ignored -> sessions.findById(paused.sessionId()).orElseThrow()
          .pause(SessionStatus.PAUSED, "USER", 1000));
      return withEvidence(paused.goalId(), "paused concept");
    });
    analyze(paused, request(null));
    assertTrue(evidenceOf(paused.sessionId()).isEmpty());

    var unmatched = fixture();
    when(ai.analyze(any(), any(), anyLong())).thenReturn(withEvidence(unmatched.goalId() + 999,
        "other goal concept"));
    analyze(unmatched, request(null));
    assertTrue(evidenceOf(unmatched.sessionId()).isEmpty());

    var excluded = fixture();
    when(ai.analyze(any(), any(), anyLong())).thenReturn(withEvidence(excluded.goalId(),
        "excluded concept"));
    analyze(excluded, request(GuardStatus.EXCLUDED));
    assertTrue(evidenceOf(excluded.sessionId()).isEmpty());
  }

  @Test
  void retentionRemovesReviewEvidenceNinetyDaysAfterSessionEnds() {
    var expired = fixture();
    var recent = fixture();
    for (var f : List.of(expired, recent)) {
      when(ai.analyze(any(), any(), anyLong())).thenReturn(withEvidence(f.goalId(),
          "retained concept"));
      analyze(f, request(null));
    }
    Instant end = Instant.now().minus(Duration.ofDays(90)).truncatedTo(ChronoUnit.MICROS);
    tx.executeWithoutResult(ignored -> {
      sessions.findById(expired.sessionId()).orElseThrow().end(end, 1000);
      sessions.findById(recent.sessionId()).orElseThrow().end(end.plus(Duration.ofDays(1)),
          1000);
    });

    retention.purgeDue(end.plus(Duration.ofDays(90)).minusSeconds(1));
    assertEquals(1, evidenceOf(expired.sessionId()).size());

    retention.purgeDue(end.plus(Duration.ofDays(90)));
    assertTrue(evidenceOf(expired.sessionId()).isEmpty());
    assertEquals(1, evidenceOf(recent.sessionId()).size());
  }

  @Test
  void historyReturnsEmptyListOnlyForAnOwnedSession() throws Exception {
    Fixture owner = fixture();
    Fixture other = fixture();
    String bearer = "Bearer " + owner.login().accessToken();
    mvc.perform(get(path(owner.sessionId())).header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
    for (String id : new String[] {Long.toString(other.sessionId()),
        Long.toString(Long.MAX_VALUE), "invalid"}) {
      mvc.perform(get("/api/v1/sessions/{sessionId}/analysis-runs", id)
          .header("Authorization", bearer))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }
    mvc.perform(get(path(owner.sessionId()))).andExpect(status().isUnauthorized());
  }

  @Test
  void historyRejectsLinkedExtension() throws Exception {
    Fixture f = fixture();
    var linked = pairing.claim(new ClaimRequest(pairing.issue(f.principal()).codeDisplay(),
        "history-extension-" + UUID.randomUUID()), "127.0.0.173");
    mvc.perform(get(path(f.sessionId()))
        .header("Authorization", "Bearer " + linked.accessToken()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SESSION_QUERY_FORBIDDEN"));
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
    assertTrue(runs.findCurrent(
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

  @ParameterizedTest
  @EnumSource(value = ContentSource.class, names = {"CHROME_DOM", "TAB_META"})
  void webInputRequiresHostBeforeStorageOrAi(ContentSource contentSource) throws Exception {
    Fixture f = fixture();
    var source = request(null);
    long before = runs.count();
    for (Metadata metadata : new Metadata[] {null, new Metadata("Chrome", null, "test", null),
        new Metadata("Chrome", null, "test", "")}) {
      var missingHost = new Request(UUID.randomUUID().toString(), contentSource,
          source.observedAt(), BigDecimal.ONE, null, metadata,
          contentSource == ContentSource.CHROME_DOM ? source.contentPayload() : null);
      mvc.perform(call(f, missingHost)).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    assertEquals(before, runs.count());
    verify(ai, never()).analyze(any(), any(), anyLong());
    for (var guard : List.of(GuardStatus.EXCLUDED, GuardStatus.PRIVACY_BLOCKED)) {
      var blocked = new Request(UUID.randomUUID().toString(), contentSource,
          source.observedAt(), BigDecimal.ONE, guard, null, null);
      mvc.perform(call(f, blocked)).andExpect(status().isOk());
    }
  }

  @Test
  void domMetadataWithoutBodyStillRequiresHostAndAppliesExclusions() throws Exception {
    Fixture f = fixture();
    long before = runs.count();
    for (String host : new String[] {null, ""}) {
      var request = new Request(UUID.randomUUID().toString(), ContentSource.CHROME_DOM,
          Instant.now(), BigDecimal.ONE, null, new Metadata("Chrome", null, "title", host), null);
      mvc.perform(call(f, request)).andExpect(status().isBadRequest());
    }
    assertEquals(before, runs.count());
    tx.executeWithoutResult(ignored -> exclusions.save(AnalysisExcludedDomainEntity.create(
        users.getReferenceById(f.principal().userId()), "example.com", Instant.now())));
    var request = new Request(UUID.randomUUID().toString(), ContentSource.CHROME_DOM,
        Instant.now(), BigDecimal.ONE, null,
        new Metadata("Chrome", null, "title", "example.com"), null);
    mvc.perform(call(f, request)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("EXCLUDED"));
    verify(ai, never()).analyze(any(), any(), anyLong());
  }

  @ParameterizedTest
  @ValueSource(strings = {"+300000-01-01T00:00:00Z", "-5000-01-01T00:00:00Z",
      "+294277-01-01T00:00:00Z", "+294276-12-31T23:59:59.999999500Z"})
  void unrepresentableTimestampsAreRejectedBeforeStorage(String time) throws Exception {
    Fixture f = fixture();
    long before = runs.count();
    mvc.perform(call(f, ordered(ContentSource.APP_META, 1, 1L, Instant.parse(time), null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertEquals(before, runs.count());
    verify(ai, never()).analyze(any(), any(), anyLong());
  }

  @ParameterizedTest
  @ValueSource(strings = {"-4713-11-24T00:00:00Z", "+294276-12-31T23:59:59.999999Z"})
  void representableTimestampBoundariesRemainAccepted(String time) throws Exception {
    Fixture f = fixture();
    mvc.perform(call(f, ordered(ContentSource.APP_META, 1, 1L, Instant.parse(time), null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("RELATED"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"REVOKE", "CONSENT", "RESUME"})
  void actualHttpWithOsivReloadsStateCommittedDuringAi(String change) throws Exception {
    Fixture f = fixture();
    try (var worker = Executors.newSingleThreadExecutor()) {
      when(ai.analyze(any(), any(), anyLong())).thenAnswer(invocation -> {
        worker.submit(() -> tx.executeWithoutResult(ignored -> {
          if (change.equals("REVOKE")) {
            var family = families.findById(f.principal().tokenFamilyId()).orElseThrow();
            family.setStatus(TokenFamilyEntity.Status.REVOKED);
            family.setRevokedReason(TokenFamilyEntity.RevokedReason.LOGOUT);
            family.setRevokedAt(Instant.now());
          } else if (change.equals("CONSENT")) {
            consents.findByUserIdAndConsentType(f.principal().userId(), ConsentType.SCREEN_ANALYSIS)
                .orElseThrow().apply(false, "2026-09-17", "SETTINGS", Instant.now());
          } else {
            var session = sessions.findById(f.sessionId()).orElseThrow();
            Instant previous = session.getLastResumedAt();
            session.pause(SessionStatus.PAUSED, "USER", 1000);
            session.resume(previous.plusSeconds(1));
          }
        })).get(10, TimeUnit.SECONDS);
        return related();
      });
      var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
          "http://127.0.0.1:" + port + path(f.sessionId())))
          .header("Authorization", "Bearer " + f.login().accessToken())
          .header("X-Device-Id", f.login().device().deviceId())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              json.writeValueAsString(request(null)))).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(change.equals("REVOKE") ? 401 : 200, response.statusCode());
      var result = json.readTree(response.body());
      if (change.equals("REVOKE")) {
        assertEquals("AUTH_RELOGIN_REQUIRED", result.path("code").asString());
      } else {
        assertEquals(change.equals("CONSENT") ? "PRIVACY_BLOCKED" : "UNCERTAIN",
            result.path("data").path("relation").asString());
      }
    }
  }

  @Test
  void excludedTabTitleWithKnownHostSkipsAi() throws Exception {
    Fixture f = fixture();
    tx.executeWithoutResult(ignored -> exclusions.save(AnalysisExcludedDomainEntity.create(
        users.getReferenceById(f.principal().userId()), "example.com", Instant.now())));
    var r = new Request(UUID.randomUUID().toString(), ContentSource.TAB_META,
        Instant.now(), BigDecimal.ONE, null,
        new Metadata("Chrome", null, "excluded synthetic title", "EXAMPLE.COM."), null);
    mvc.perform(call(f, r)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("EXCLUDED"));
    verify(ai, never()).analyze(any(), any(), anyLong());
  }

  @ParameterizedTest
  @ValueSource(longs = {-60, 60})
  void deviceClockOffsetDoesNotRejectOrSuppressInitialObservation(long offset) throws Exception {
    Fixture f = fixture(Instant.now().minusSeconds(1));
    assertClockOffsetAccepted(f, offset);
  }

  @ParameterizedTest
  @ValueSource(longs = {-60, 60})
  void deviceClockOffsetDoesNotSuppressObservationAfterResume(long offset) throws Exception {
    Fixture f = fixture();
    tx.executeWithoutResult(ignored -> sessions.findById(f.sessionId()).orElseThrow()
        .pause(SessionStatus.PAUSED, "USER", 1000));
    tx.executeWithoutResult(ignored -> sessions.findById(f.sessionId()).orElseThrow()
        .resume(Instant.now()));
    assertClockOffsetAccepted(f, offset);
  }

  private void assertClockOffsetAccepted(Fixture f, long offset) throws Exception {
    var observed = Instant.now().plusSeconds(offset)
        .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    var base = request(null);
    var r = new Request(base.clientRunId(), base.contentSource(), observed, BigDecimal.ONE,
        null, base.metadata(), base.contentPayload());
    Instant before = Instant.now();
    String body = mvc.perform(call(f, r)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relation").value("RELATED"))
        .andReturn().getResponse().getContentAsString();
    var run = runs.findById(Long.parseLong(json.readTree(body).at("/data/analysisRunId")
        .asString())).orElseThrow();
    assertEquals(observed, run.getObservedAt());
    assertFalse(run.getReceivedAt().isBefore(before.minusMillis(1)));
    assertFalse(run.getReceivedAt().isAfter(Instant.now().plusMillis(1)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oldAiCompletionCannotOverwriteLatestExcludedAcrossSources(
      boolean numbered) throws Exception {
    Fixture f = fixture();
    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    when(ai.analyze(any(), any(), anyLong())).thenAnswer(invocation -> {
      started.countDown();
      assertTrue(finish.await(10, TimeUnit.SECONDS));
      return related();
    });
    Request base = request(null);
    Request first = numbered ? new Request(base.clientRunId(), base.contentSource(),
        base.observedAt().plusSeconds(3600), base.sequence(), null, base.metadata(),
        base.contentPayload(), BigDecimal.ONE) : base;
    try (var executor = Executors.newSingleThreadExecutor()) {
      var pending = executor.submit(() -> service.analyze(f.principal(),
          f.login().device().deviceId(), Long.toString(f.sessionId()), first));
      assertTrue(started.await(10, TimeUnit.SECONDS));
      try {
        var guard = new Request(UUID.randomUUID().toString(), ContentSource.APP_META,
            numbered ? base.observedAt() : first.observedAt().plusMillis(1), BigDecimal.ONE,
            GuardStatus.EXCLUDED, null, null, numbered ? BigDecimal.valueOf(2) : null);
        assertEquals(Relation.EXCLUDED, service.analyze(f.principal(),
            f.login().device().deviceId(), Long.toString(f.sessionId()), guard).relation());
      } finally {
        finish.countDown();
      }
      assertEquals(Relation.RELATED, pending.get(10, TimeUnit.SECONDS).relation());
    }
    var latest = runs.findCurrent(
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
    tx.executeWithoutResult(ignored -> sessions.findById(f.sessionId()).orElseThrow()
        .resume(prepared.runningSince().plusSeconds(1)));
    assertEquals(SessionStatus.RUNNING, sessions.findById(f.sessionId()).orElseThrow().getStatus());
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
        new Metadata("Chrome", null, "test", "example.com"), null);
    assertEquals(Relation.UNRELATED, service.analyze(f.principal(),
        f.login().device().deviceId(),
        Long.toString(f.sessionId()), request).relation());
    assertEquals(Relation.UNCERTAIN,
        runs.findCurrent(
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

  @Test
  void globalOrderSurvivesClockRollbackAndIgnoresDelayedAndLegacyRequests() {
    Fixture f = fixture();
    Instant future = Instant.now().plusSeconds(3600);
    assertEquals(Relation.RELATED, analyze(f, ordered(ContentSource.APP_META, 1, 10L,
        future, null)).relation());
    assertEquals(Relation.EXCLUDED, analyze(f, ordered(ContentSource.DESKTOP_APP_OCR, 1, 11L,
        future.minusSeconds(3600), GuardStatus.EXCLUDED)).relation());
    // 큰 벽시각이라도 전역 번호가 작으면 과거 이력이며 현재 보호 상태를 바꾸지 않는다.
    assertEquals(Relation.UNCERTAIN, analyze(f, ordered(ContentSource.APP_META, 2, 9L,
        future.plusSeconds(10), null)).relation());
    analyze(f, ordered(ContentSource.APP_META, 3, null,
        future.plusSeconds(20), GuardStatus.PRIVACY_BLOCKED));
    var current = runs.findCurrent(f.sessionId(), f.principal().deviceId()).orElseThrow();
    assertEquals(11L, current.getObservationSequence());
    assertEquals(Relation.EXCLUDED, current.getRelation());
    assertEquals(Relation.RELATED, analyze(f, ordered(ContentSource.APP_META, 4, 12L,
        future.minusSeconds(7200), null)).relation());
    assertEquals(12L, runs.findCurrent(f.sessionId(), f.principal().deviceId())
        .orElseThrow().getObservationSequence());
    verify(ai, times(2)).analyze(any(), any(), anyLong());
  }

  @Test
  void legacyRollbackQuarantinesAndPreservesProtectionUntilNumberedRecovery() {
    Fixture f = fixture();
    Instant future = Instant.now().plusSeconds(3600);
    analyze(f, ordered(ContentSource.APP_META, 1, null, future, null));
    assertEquals(Relation.UNCERTAIN, analyze(f, ordered(ContentSource.APP_META, 2, null,
        future.minusSeconds(3600), null)).relation());
    var current = runs.findCurrent(f.sessionId(), f.principal().deviceId()).orElseThrow();
    assertTrue(current.isOrderingUntrusted());
    assertEquals(2, current.getSequence());
    assertEquals(Relation.EXCLUDED, analyze(f, ordered(ContentSource.DESKTOP_APP_OCR, 1, null,
        future.minusSeconds(3700), GuardStatus.EXCLUDED)).relation());
    assertEquals(Relation.EXCLUDED, analyze(f, ordered(ContentSource.APP_META, 3, null,
        future.plusSeconds(20), null)).relation());
    assertEquals(Relation.PRIVACY_BLOCKED, analyze(f, ordered(ContentSource.APP_META, 4, null,
        future.minusSeconds(3800), GuardStatus.PRIVACY_BLOCKED)).relation());
    assertEquals(Relation.PRIVACY_BLOCKED, analyze(f, ordered(ContentSource.DESKTOP_APP_OCR, 2,
        null, future.plusSeconds(30), null)).relation());
    assertEquals(Relation.RELATED, analyze(f, ordered(ContentSource.APP_META, 5, 1L,
        future.minusSeconds(3900), null)).relation());
    current = runs.findCurrent(f.sessionId(), f.principal().deviceId()).orElseThrow();
    assertFalse(current.isOrderingUntrusted());
    assertEquals(1L, current.getObservationSequence());
    verify(ai, times(2)).analyze(any(), any(), anyLong());
  }

  @Test
  void oldSourceSequenceCannotTriggerClockQuarantineOrOverrideCurrent() {
    Fixture f = fixture();
    Instant time = Instant.now();
    analyze(f, ordered(ContentSource.APP_META, 2, null, time, null));
    var old = analyze(f, ordered(ContentSource.APP_META, 1, null,
        time.plusSeconds(1000), null));
    assertEquals(Relation.UNCERTAIN, old.relation());
    var current = runs.findCurrent(f.sessionId(), f.principal().deviceId()).orElseThrow();
    assertEquals(2, current.getSequence());
    assertFalse(current.isOrderingUntrusted());
    assertEquals(Relation.RELATED, analyze(f, ordered(ContentSource.APP_META, 3, null,
        time.plusSeconds(1), null)).relation());
  }

  @Test
  void globalSequenceDuplicatesAndInvalidValuesAreRejectedOverHttp() throws Exception {
    Fixture f = fixture();
    var r = ordered(ContentSource.APP_META, 1, 42L, Instant.now(), null);
    mvc.perform(call(f, r)).andExpect(status().isOk());
    mvc.perform(call(f, ordered(ContentSource.DESKTOP_APP_OCR, 1, 42L, Instant.now(), null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    String body = json.writeValueAsString(r);
    for (String invalid : new String[] {"0", "-1", "1.5", "9007199254740992"}) {
      mvc.perform(post(path(f.sessionId())).header("Authorization",
              "Bearer " + f.login().accessToken())
          .header("X-Device-Id", f.login().device().deviceId())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.replace("\"observationSequence\":42",
              "\"observationSequence\":" + invalid)))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void globalSequenceIsScopedToSessionAndDevice() throws Exception {
    Fixture first = fixture();
    assertEquals(Relation.RELATED, analyze(first,
        ordered(ContentSource.APP_META, 1, 1L, Instant.now(), null)).relation());
    var linked = pairing.claim(new ClaimRequest(pairing.issue(first.principal()).codeDisplay(),
        "analysis-sequence-" + UUID.randomUUID()), "127.0.0.171");
    // Same session, different device: a session-only duplicate check would reject this.
    mvc.perform(post(path(first.sessionId()))
        .header("Authorization", "Bearer " + linked.accessToken())
        .header("X-Device-Id", linked.device().deviceId()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(ordered(ContentSource.TAB_META, 1, 1L,
            Instant.now(), null)))).andExpect(status().isOk());
    var nextId = tx.execute(ignored -> {
      var old = sessions.findById(first.sessionId()).orElseThrow();
      old.abandon(Instant.now());
      sessions.flush();
      return sessions.saveAndFlush(LearningSessionEntity.create(old.getUser(), old.getGoal(),
          old.getRunnerDevice(), 1800000, "Asia/Seoul", LocalDate.now(), "{}", "v1",
          Instant.now())).getId();
    });
    // Same device, different session: a device-only duplicate check would reject this.
    var next = new Fixture(first.login(), first.principal(), nextId, first.goalId());
    assertEquals(Relation.RELATED,
        analyze(next, ordered(ContentSource.APP_META, 1, 1L, Instant.now(), null)).relation());
  }

  @Test
  void rollbackGuardOnAnotherLegacySourceImmediatelyProtectsCurrent() {
    Fixture f = fixture();
    Instant future = Instant.now().plusSeconds(3600);
    analyze(f, ordered(ContentSource.APP_META, 1, null, future, null));
    analyze(f, ordered(ContentSource.DESKTOP_APP_OCR, 1, null,
        future.minusSeconds(3600), GuardStatus.PRIVACY_BLOCKED));
    var current = runs.findCurrent(f.sessionId(), f.principal().deviceId()).orElseThrow();
    assertTrue(current.isOrderingUntrusted());
    assertEquals(Relation.PRIVACY_BLOCKED, current.getRelation());
    assertEquals(Relation.PRIVACY_BLOCKED, analyze(f, ordered(ContentSource.APP_META, 2, null,
        future.plusSeconds(1), null)).relation());
    verify(ai, times(1)).analyze(any(), any(), anyLong());
  }

  @Test
  void concurrentGlobalSequenceDuplicatesCreateExactlyOneObservation() throws Exception {
    Fixture f = fixture();
    var start = new CountDownLatch(1);
    var ready = new CountDownLatch(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return mvc.perform(call(f, ordered(ContentSource.APP_META, 1, 1L, Instant.now(),
            GuardStatus.EXCLUDED))).andReturn().getResponse().getStatus();
      })).toList();
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      var results = new java.util.ArrayList<Integer>();
      for (var task : tasks) {
        results.add(task.get(10, TimeUnit.SECONDS));
      }
      results.sort(Integer::compareTo);
      assertEquals(java.util.List.of(200, 409), results);
    }
    assertEquals(1L, runs.findCurrent(f.sessionId(), f.principal().deviceId())
        .orElseThrow().getObservationSequence());
  }

  private com.capstone.backend.analysis.dto.AnalysisDtos.Response analyze(Fixture fixture,
      Request request) {
    return service.analyze(fixture.principal(), fixture.login().device().deviceId(),
        Long.toString(fixture.sessionId()), request);
  }

  private static Request ordered(ContentSource source, long sequence, Long global,
      Instant observedAt, GuardStatus guard) {
    boolean meta = source == ContentSource.APP_META || source == ContentSource.TAB_META;
    return new Request(UUID.randomUUID().toString(), source, observedAt,
        BigDecimal.valueOf(sequence), guard,
        guard == null ? new Metadata("Xcode", "synthetic", "synthetic", "example.com") : null,
        guard == null && !meta ? new ContentPayload("synthetic text") : null,
        global == null ? null : BigDecimal.valueOf(global));
  }

  private Fixture fixture() {
    return fixture(Instant.now().minusSeconds(120));
  }

  private Fixture fixture(Instant startedAt) {
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
          1800000, "Asia/Seoul", LocalDate.now(), "{}", "v1", startedAt));
      return new Fixture(account, new FocusOnPrincipal(user.getId(), device.getId(), family.getId(),
          device.getDeviceType()), session.getId(), goal.getId());
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

  private static AnalysisDecision matched(long goalId) {
    return new AnalysisDecision(Relation.RELATED, .9, ReasonCode.SEMANTIC_GOAL_MATCH,
        AnalysisStatus.COMPLETED, "test-model", "test-prompt",
        com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus.MATCHED, goalId);
  }

  private List<LearningReviewEvidenceEntity> evidenceOf(long sessionId) {
    return evidences.findInput(sessionId, Instant.now().plusSeconds(60),
        org.springframework.data.domain.PageRequest.of(0, 20));
  }

  private static AnalysisDecision withEvidence(long goalId, String label) {
    return new AnalysisDecision(Relation.RELATED, .9, ReasonCode.SEMANTIC_GOAL_MATCH,
        AnalysisStatus.COMPLETED, "test-model", "test-prompt",
        com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus.MATCHED, goalId,
        new AnalysisDecision.ReviewEvidence(label, AnalysisDecision.EvidenceType.CONCEPT));
  }

  private static AnalysisDecision related() {
    return new AnalysisDecision(Relation.RELATED, .9, ReasonCode.SEMANTIC_GOAL_MATCH,
        AnalysisStatus.COMPLETED, "test-model", "test-prompt");
  }

  private record Fixture(LoginResponse login, FocusOnPrincipal principal, long sessionId,
      long goalId) {}
}
