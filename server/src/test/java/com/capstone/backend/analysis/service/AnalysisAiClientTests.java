package com.capstone.backend.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.ContentPayload;
import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.Metadata;
import com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.service.AnalysisDecision.EvidenceType;
import com.capstone.backend.analysis.service.AnalysisDecision.ReviewEvidence;
import com.capstone.backend.analysis.service.AnalysisRunStore.Prepared;
import com.capstone.backend.common.api.SafeApiLog;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

/** 실제 루프백 AI stub과 schema 검증으로 전송·오류 경계를 검증한다. */
class AnalysisAiClientTests {
  private static final String CONCEPT = "React useState";
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void calibratedBoundaryAndBetaException() {
    assertEquals(Relation.RELATED, decision("RELATED", "0.6", "SEMANTIC_GOAL_MATCH", "v1")
        .relation());
    assertEquals(Relation.UNCERTAIN, decision("UNRELATED", "0.599", "SEMANTIC_GOAL_MISMATCH",
        "v1").relation());
    var candidate = decision("UNRELATED", "null", "SEMANTIC_GOAL_MISMATCH", "v1-uncalibrated");
    assertEquals(Relation.UNRELATED, candidate.relation());
    assertNull(candidate.confidence());
    assertEquals(AnalysisStatus.COMPLETED, candidate.status());
    assertEquals(Relation.UNCERTAIN, decision("RELATED", "null", "SEMANTIC_GOAL_MATCH", "v1")
        .relation());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
      "0.0, UNCERTAIN", "0.599, UNCERTAIN", "0.6, UNRELATED", "0.6001, UNRELATED",
      "0.65, UNRELATED", "0.749, UNRELATED", "0.75, UNRELATED", "1.0, UNRELATED"
  })
  void numericBetaAndNormalResultsUseTheSamePointSixBoundary(String score, Relation expected) {
    for (String prompt : new String[] {"v1", "v1-uncalibrated"}) {
      var result = decision("UNRELATED", score, "SEMANTIC_GOAL_MISMATCH", prompt);
      assertEquals(expected, result.relation());
      if (expected == Relation.UNCERTAIN) {
        assertNull(result.confidence());
        assertEquals(ReasonCode.INSUFFICIENT_CONTEXT, result.reasonCode());
      } else {
        assertEquals(Double.parseDouble(score), result.confidence());
        assertEquals(AnalysisStatus.COMPLETED, result.status());
      }
      var related = decision("RELATED", score, "SEMANTIC_GOAL_MATCH", prompt);
      assertEquals(expected == Relation.UNRELATED ? Relation.RELATED : Relation.UNCERTAIN,
          related.relation());
    }
  }

  /** matchStatus는 확정 RELATED일 때만 남고, 없거나 잘못된 값이면 판정은 유지한 채 비운다. */
  @Test
  void matchStatusIsKeptOnlyForConfirmedRelated() {
    for (String status : new String[] {"MATCHED", "FAR", "NOT_ENOUGH"}) {
      var related = withMatch("RELATED", "0.9", "SEMANTIC_GOAL_MATCH", "\"" + status + "\"");
      assertEquals(Relation.RELATED, related.relation());
      assertEquals(status, related.matchStatus().name());
    }
    for (String invalid : new String[] {"\"far\"", "\"SOMETHING\"", "1", "null", "{}"}) {
      var related = withMatch("RELATED", "0.9", "SEMANTIC_GOAL_MATCH", invalid);
      assertEquals(Relation.RELATED, related.relation());
      assertEquals(AnalysisStatus.COMPLETED, related.status());
      assertNull(related.matchStatus());
    }
    assertNull(decision("RELATED", "0.9", "SEMANTIC_GOAL_MATCH", "v1").matchStatus());
    assertNull(withMatch("UNRELATED", "0.9", "SEMANTIC_GOAL_MISMATCH", "\"FAR\"")
        .matchStatus());
    var lowered = withMatch("RELATED", "0.5", "SEMANTIC_GOAL_MATCH", "\"MATCHED\"");
    assertEquals(Relation.UNCERTAIN, lowered.relation());
    assertNull(lowered.matchStatus());
    assertNull(withMatch("UNCERTAIN", "null", "INSUFFICIENT_CONTEXT", "\"MATCHED\"")
        .matchStatus());
  }

  private AnalysisDecision withMatch(String relation, String score, String reason,
      String match) {
    String body = result(relation, score, reason, "v1");
    return AnalysisAiClient.normalize(json.readTree(
        body.substring(0, body.length() - 1) + ",\"matchStatus\":" + match + "}"), true);
  }

  @Test
  void missingOrInvalidEvidenceLeavesMatchedDecisionIntact() {
    var response = json.readTree(matchedResult()).asObject();
    var original = AnalysisAiClient.normalize(response, true);
    assertEquals(Relation.RELATED, original.relation());
    assertEquals(MatchStatus.MATCHED, original.matchStatus());
    assertEquals(30L, original.matchedGoalId());
    assertNull(original.reviewEvidence());
    for (String invalid : new String[] {"null", "[]", "{}", "1", "\"CONCEPT\"", "[null]", "[1]",
        "[{\"conceptLabel\":\"React\"}]", "[{\"evidenceType\":\"CONCEPT\"}]",
        "[{\"conceptLabel\":42,\"evidenceType\":\"CONCEPT\"}]",
        evidence("React", "UNKNOWN"), evidence("React", "concept"), evidence("", "CONCEPT"),
        evidence(" \u00a0 ", "CONCEPT"), evidence("x".repeat(121), "CONCEPT"),
        evidence("React\nuseState", "CONCEPT"), evidence("https://example.com/private", "CONCEPT"),
        evidence("www.example.com", "CONCEPT"), evidence("student@example.com", "CONCEPT"),
        evidence("//example.com/private", "CONCEPT"), evidence("file:/private/example", "CONCEPT"),
        evidence("data:text/plain,example", "CONCEPT"),
        evidence("Bear\u200ber syntheticPrivateToken", "CONCEPT"),
        "[{\"conceptLabel\":\"React\",\"evidenceType\":\"CONCEPT\",\"title\":\"raw\"}]",
        "[{\"conceptLabel\":\"React\",\"evidenceType\":\"CONCEPT\"},null]"}) {
      response.set("reviewEvidence", json.readTree(invalid));
      assertEquals(original, AnalysisAiClient.normalize(response, true));
    }
  }

  @Test
  void onlyConfirmedMatchedDecisionsCanCarryEvidence() {
    var response = json.readTree(matchedResult()).asObject();
    response.set("reviewEvidence", json.readTree(evidence(CONCEPT, "CONCEPT")));
    var accepted = AnalysisAiClient.normalize(response, true);
    assertEquals(new ReviewEvidence(CONCEPT, EvidenceType.CONCEPT), accepted.reviewEvidence());
    assertFalse(accepted.toString().contains(CONCEPT));
    assertTrue(json.valueToTree(accepted).path("reviewEvidence").isMissingNode());

    String boundary = "학".repeat(119) + "𐐷";
    response.set("reviewEvidence", json.readTree(evidence(boundary, "CONCEPT")));
    assertEquals(boundary, AnalysisAiClient.normalize(response, true)
        .reviewEvidence().conceptLabel());
    for (String match : new String[] {"FAR", "NOT_ENOUGH", "UNKNOWN"}) {
      response.put("matchStatus", match);
      assertNull(AnalysisAiClient.normalize(response, true).reviewEvidence());
    }
    response.put("matchStatus", "MATCHED");
    response.put("matchedGoalId", "invalid");
    assertNull(AnalysisAiClient.normalize(response, true).reviewEvidence());
    response.put("matchedGoalId", "30");
    response.put("confidence", 0.59);
    assertEquals(Relation.UNCERTAIN, AnalysisAiClient.normalize(response, true).relation());
    assertNull(AnalysisAiClient.normalize(response, true).reviewEvidence());
    response.put("confidence", 0.9);
    response.put("decision", "UNRELATED");
    response.put("reasonCode", "SEMANTIC_GOAL_MISMATCH");
    var unrelated = AnalysisAiClient.normalize(response, true);
    assertEquals(Relation.UNRELATED, unrelated.relation());
    assertNull(unrelated.reviewEvidence());
  }

  @ParameterizedTest
  @CsvSource({
      "CHROME_DOM, body, true", "DESKTOP_APP_OCR, body, true",
      "CHROME_DOM, , false", "DESKTOP_APP_OCR, '   ', false",
      "CHROME_DOM, '\u00a0\u200b', false", // No-break space and zero-width space.
      "APP_META, , false", "TAB_META, , false"
  })
  void evidenceThroughHttpRequiresAnObservationBody(ContentSource source, String body,
      boolean expected) throws Exception {
    var response = json.readTree(matchedResult()).asObject();
    response.set("reviewEvidence", json.readTree(evidence(CONCEPT, "CONCEPT")));
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/internal/v1/analysis-runs", exchange -> {
      byte[] answer = json.writeValueAsBytes(response);
      try (exchange) {
        exchange.sendResponseHeaders(200, answer.length);
        exchange.getResponseBody().write(answer);
      }
    });
    server.start();
    try {
      var client = new AnalysisAiClient(json, "http://127.0.0.1:"
          + server.getAddress().getPort(), "service-test-only");
      var request = new Request(UUID.randomUUID().toString(), source, Instant.now(),
          BigDecimal.ONE, null, new Metadata("Chrome", CONCEPT, CONCEPT, "example.com"),
          body == null ? null : new ContentPayload(body));
      var prepared = new Prepared(10, 20, 30, 0, Instant.now(), CONCEPT, "v1", null, true, null);
      var decision = client.analyze(prepared, request, 40);
      assertEquals(Relation.RELATED, decision.relation());
      assertEquals(MatchStatus.MATCHED, decision.matchStatus());
      assertEquals(expected ? new ReviewEvidence(CONCEPT, EvidenceType.CONCEPT) : null,
          decision.reviewEvidence());
    } finally {
      server.stop(0);
    }
  }

  private String evidence(String label, String type) {
    var array = json.createArrayNode();
    array.addObject().put("conceptLabel", label).put("evidenceType", type);
    return array.toString();
  }

  private static String matchedResult() {
    return """
        {"decision":"RELATED","confidence":null,"reasonCode":"SEMANTIC_GOAL_MATCH",
         "modelVersion":"model-test","promptVersion":"v1-uncalibrated","matchStatus":"MATCHED",
         "matchedGoalId":"30"}
        """;
  }

  @Test
  void receiptMalformedAndContradictoryResultsAreUnavailable() {
    for (String value : new String[] {"{}", "null", "[]", "{\"status\":\"RECEIVED\"}",
        result("RELATED", "1.1", "SEMANTIC_GOAL_MATCH", "v1"),
        result("RELATED", "\"0.9\"", "SEMANTIC_GOAL_MATCH", "v1"),
        result("RELATED", "0.9", "SEMANTIC_GOAL_MISMATCH", "v1"),
        result("EXCLUDED", "null", "INSUFFICIENT_CONTEXT", "v1"),
        result("UNCERTAIN", "null", "SEMANTIC_GOAL_MATCH", "v1"),
        result("SOMETHING", "null", "INSUFFICIENT_CONTEXT", "v1")}) {
      assertEquals(ReasonCode.ANALYSIS_UNAVAILABLE,
          AnalysisAiClient.normalize(json.readTree(value), true).reasonCode());
    }
  }

  @Test
  void forwardsServerSnapshotServiceKeyTraceAndPublicInputOnlyOnce() throws Exception {
    var body = new AtomicReference<String>();
    var requests = new AtomicInteger();
    var auth = new AtomicReference<String>();
    var trace = new AtomicReference<String>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/internal/v1/analysis-runs", exchange -> {
      requests.incrementAndGet();
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      trace.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
      byte[] answer = result("RELATED", "0.91", "SEMANTIC_GOAL_MATCH", "v1")
          .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, answer.length);
      exchange.getResponseBody().write(answer);
      exchange.close();
    });
    server.start();
    var logger = (Logger) LoggerFactory.getLogger(SafeApiLog.class);
    var logs = new ListAppender<ILoggingEvent>();
    logs.start();
    logger.addAppender(logs);
    try {
      String id = UUID.randomUUID().toString();
      MDC.put("traceId", id);
      var client = new AnalysisAiClient(json, "http://127.0.0.1:"
          + server.getAddress().getPort(), "service-test-only");
      var request = new Request(id, ContentSource.CHROME_DOM, Instant.now(), BigDecimal.ONE, null,
          new Metadata("Chrome", null, "synthetic title", "EXAMPLE.COM."),
          new ContentPayload("synthetic body"), BigDecimal.valueOf(105));
      var p = new Prepared(10, 20, 30, 0, Instant.now(), "서버 목표", "v1", null, true, null);
      assertEquals(Relation.RELATED, client.analyze(p, request, 40).relation());
      assertEquals(1, requests.get());
      assertEquals("Bearer service-test-only", auth.get());
      assertEquals(id, trace.get());
      var envelope = json.readTree(body.get());
      assertEquals("서버 목표", envelope.at("/request/goalSnapshot").asString());
      assertEquals("30", envelope.at("/request/goals/0/goalId").asString());
      assertEquals("서버 목표", envelope.at("/request/goals/0/goalText").asString());
      assertEquals(1, envelope.at("/request/goalVersion").asInt());
      assertEquals(1, envelope.at("/context/goalVersion").asInt());
      assertEquals(1, envelope.at("/goal/goalVersion").asInt());
      assertEquals("40", envelope.at("/context/userId").asString());
      assertEquals(1, envelope.at("/context/stateVersion").asInt());
      assertEquals("example.com", envelope.at("/request/metadata/urlHost").asString());
      assertEquals("synthetic body", envelope.at("/request/contentPayload/text").asString());
      assertTrue(envelope.at("/request/observationSequence").isMissingNode());
      var event = logs.list.getLast();
      var fields = event.getKeyValuePairs().toString();
      assertEquals(id, event.getMDCPropertyMap().get("traceId"));
      assertEquals("ai.call.ok", event.getMessage());
      assertTrue(fields.contains("relation=\"RELATED\""));
      assertTrue(fields.contains("confidence=\"0.91\""));
      assertTrue(fields.contains("reasonCode=\"SEMANTIC_GOAL_MATCH\""));
      assertTrue(fields.contains("upstreamStatus=\"200\""));
      assertSafeResultLog(event, "synthetic body", "synthetic title", "service-test-only", "서버 목표");
    } finally {
      logger.detachAppender(logs);
      logs.stop();
      MDC.remove("traceId");
      server.stop(0);
    }
  }

  @Test
  void oversizedUpstreamFailureKeepsStatusWithoutLoggingResponseBody() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/internal/v1/analysis-runs", exchange -> {
      byte[] body = ("private AI error body " + "x".repeat(8192))
          .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(503, body.length);
      try {
        exchange.getResponseBody().write(body);
      } finally {
        exchange.close();
      }
    });
    var logger = (Logger) LoggerFactory.getLogger(SafeApiLog.class);
    var logs = new ListAppender<ILoggingEvent>();
    logs.start();
    logger.addAppender(logs);
    server.start();
    try {
      var client = new AnalysisAiClient(json, "http://127.0.0.1:"
          + server.getAddress().getPort(), "service-test-only");
      var request = new Request(UUID.randomUUID().toString(), ContentSource.APP_META,
          Instant.now(), BigDecimal.ONE, null, null, null);
      var prepared = new Prepared(1, 2, 3, 0, Instant.now(), "목표", "v1", null, true, null);
      assertEquals(ReasonCode.ANALYSIS_UNAVAILABLE,
          client.analyze(prepared, request, 4).reasonCode());
      var event = logs.list.getLast();
      var fields = event.getKeyValuePairs().toString();
      assertEquals("ai.call.fail", event.getMessage());
      assertTrue(fields.contains("stage=\"ai\""));
      assertTrue(fields.contains("upstreamStatus=\"503\""));
      assertTrue(fields.contains("reasonCode=\"ANALYSIS_UNAVAILABLE\""));
      assertSafeResultLog(event, "private AI error body", "service-test-only", "목표");
    } finally {
      logger.detachAppender(logs);
      logs.stop();
      server.stop(0);
    }
  }

  private static void assertSafeResultLog(ILoggingEvent event, String... privateValues) {
    var logged = event.getFormattedMessage() + event.getKeyValuePairs() + event.getMDCPropertyMap();
    for (String value : privateValues) {
      assertFalse(logged.contains(value));
    }
    assertNull(event.getThrowableProxy());
  }

  @Test
  void emptyConfigurationInvalidOriginsAndMissingKeyAreSafe() {
    var request = new Request(UUID.randomUUID().toString(), ContentSource.APP_META,
        Instant.now(), BigDecimal.ONE, null, null, null);
    var p = new Prepared(1, 2, 3, 0, Instant.now(), "목표", "v1", null, true, null);
    assertEquals(ReasonCode.ANALYSIS_UNAVAILABLE,
        new AnalysisAiClient(json, "", "").analyze(p, request, 4).reasonCode());
    for (String url : new String[] {"file:///tmp/test", "http://user:secret@127.0.0.1",
        "http://127.0.0.1/?secret=test", "http://127.0.0.1/other"}) {
      assertThrows(IllegalArgumentException.class, () -> new AnalysisAiClient(json, url, "test"));
    }
  }

  @Test
  void blankKeyWithConfiguredOriginDoesNotSendAnyRequest() throws Exception {
    var requests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/internal/v1/analysis-runs", exchange -> {
      requests.incrementAndGet();
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    server.start();
    try {
      var client = new AnalysisAiClient(json, "http://127.0.0.1:"
          + server.getAddress().getPort(), " ");
      var request = new Request(UUID.randomUUID().toString(), ContentSource.APP_META,
          Instant.now(), BigDecimal.ONE, null, null, null);
      var prepared = new Prepared(1, 2, 3, 0, Instant.now(), "목표", "v1", null, true, null);
      assertEquals(ReasonCode.ANALYSIS_UNAVAILABLE,
          client.analyze(prepared, request, 4).reasonCode());
      assertEquals(0, requests.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void oversizedValidDecisionIsRejectedByByteLimit() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/internal/v1/analysis-runs", exchange -> {
      exchange.getRequestBody().readAllBytes();
      byte[] body = (" ".repeat(9000)
          + result("RELATED", "0.91", "SEMANTIC_GOAL_MATCH", "v1"))
          .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      var client = new AnalysisAiClient(json, "http://127.0.0.1:"
          + server.getAddress().getPort(), "test-only-service-key");
      var input = new Request(UUID.randomUUID().toString(), ContentSource.APP_META,
          Instant.now(), BigDecimal.ONE, null, null, null);
      var p = new Prepared(1, 2, 3, 0, Instant.now(), "목표", "v1", null, true, null);
      assertEquals(ReasonCode.ANALYSIS_UNAVAILABLE, client.analyze(p, input, 4).reasonCode());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void stalledRequestClosesConnectionBeforeStubCleanup() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        var executor = Executors.newSingleThreadExecutor()) {
      server.setSoTimeout(8000);
      var peerClosed = executor.submit(() -> {
        try (var socket = server.accept()) {
          socket.setSoTimeout(8000);
          var input = socket.getInputStream();
          var headers = new ByteArrayOutputStream();
          while (!headers.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            int value = input.read();
            if (value == -1 || headers.size() > 16384) {
              throw new IllegalStateException("INVALID_TEST_REQUEST");
            }
            headers.write(value);
          }
          int length = headers.toString(StandardCharsets.US_ASCII).lines()
              .filter(line -> line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))
              .mapToInt(line -> Integer.parseInt(line.substring(line.indexOf(':') + 1).trim()))
              .findFirst().orElseThrow();
          assertEquals(length, input.readNBytes(length).length);
          socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 1024\r\n"
              + "Content-Type: application/json\r\nConnection: close\r\n\r\n{")
              .getBytes(StandardCharsets.US_ASCII));
          socket.getOutputStream().flush();
          // Stub을 닫기 전에 client가 연결을 닫는지 관찰한다. 시간 초과는 성공이 아니다.
          try {
            return input.read() == -1;
          } catch (SocketException reset) {
            return true;
          }
        }
      });
      var client = new AnalysisAiClient(json, "http://127.0.0.1:" + server.getLocalPort(), "test-key");
      var input = new Request(UUID.randomUUID().toString(), ContentSource.APP_META,
          Instant.now(), BigDecimal.ONE, null, null, null);
      var p = new Prepared(1, 2, 3, 0, Instant.now(), "목표", "v1", null, true, null);
      long started = System.nanoTime();
      assertEquals(ReasonCode.ANALYSIS_UNAVAILABLE, client.analyze(p, input, 4).reasonCode());
      assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(8));
      assertTrue(peerClosed.get(2, TimeUnit.SECONDS));
    }
  }

  private AnalysisDecision decision(String relation, String score, String reason, String prompt) {
    return AnalysisAiClient.normalize(json.readTree(result(relation, score, reason, prompt)), true);
  }

  private static String result(String relation, String score, String reason, String prompt) {
    return "{\"decision\":\"" + relation + "\",\"confidence\":" + score
        + ",\"reasonCode\":\"" + reason + "\",\"modelVersion\":\"model-test\","
        + "\"promptVersion\":\"" + prompt + "\"}";
  }
}
