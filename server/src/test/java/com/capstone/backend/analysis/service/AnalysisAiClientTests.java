package com.capstone.backend.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.ContentPayload;
import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.Metadata;
import com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.service.AnalysisRunStore.Prepared;
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
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

/** 실제 루프백 AI stub과 schema 검증으로 전송·오류 경계를 검증한다. */
class AnalysisAiClientTests {
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
          AnalysisAiClient.normalize(json.readTree(value)).reasonCode());
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
      assertEquals("40", envelope.at("/context/userId").asString());
      assertEquals(1, envelope.at("/context/stateVersion").asInt());
      assertEquals("example.com", envelope.at("/request/metadata/urlHost").asString());
      assertEquals("synthetic body", envelope.at("/request/contentPayload/text").asString());
      assertTrue(envelope.at("/request/observationSequence").isMissingNode());
    } finally {
      MDC.remove("traceId");
      server.stop(0);
    }
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
    return AnalysisAiClient.normalize(json.readTree(result(relation, score, reason, prompt)));
  }

  private static String result(String relation, String score, String reason, String prompt) {
    return "{\"decision\":\"" + relation + "\",\"confidence\":" + score
        + ",\"reasonCode\":\"" + reason + "\",\"modelVersion\":\"model-test\","
        + "\"promptVersion\":\"" + prompt + "\"}";
  }
}
