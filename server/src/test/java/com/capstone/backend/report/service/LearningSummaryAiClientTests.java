package com.capstone.backend.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capstone.backend.report.service.LearningSummaryAiClient.Evidence;
import com.capstone.backend.report.service.LearningSummaryAiClient.Input;
import com.capstone.backend.report.service.LearningSummaryAiClient.LearningSummaryAiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 실제 루프백 AI stub으로 내부 호출 형식과 실패의 재시도 분류를 검증한다. */
class LearningSummaryAiClientTests {
  private static final Input INPUT = new Input(7, "트랜잭션 공부",
      List.of(new Evidence(11, "격리 수준", "2026-11-19T01:00:00Z"),
          new Evidence(12, "MVCC", "2026-11-19T01:01:00Z")));
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void sendsServerFixedInputWithServiceKeyAndTrace() throws Exception {
    var path = new AtomicReference<String>();
    var headers = new AtomicReference<com.sun.net.httpserver.Headers>();
    var body = new AtomicReference<JsonNode>();
    HttpServer server = server(exchange -> {
      path.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
      headers.set(exchange.getRequestHeaders());
      body.set(json.readTree(exchange.getRequestBody().readAllBytes()));
      return new Reply(200, "{\"overview\":\"o\"}");
    });
    try {
      JsonNode result = client(server).generate(INPUT, "trace-1234-abcd");
      assertEquals("o", result.path("overview").asString());
      assertEquals("POST /internal/v1/learning-summaries", path.get());
      assertEquals("Bearer service-key", headers.get().getFirst("Authorization"));
      assertEquals("trace-1234-abcd", headers.get().getFirst("X-Trace-Id"));
      assertEquals(json.readTree("""
          {"sessionId":"7","goalSnapshot":"트랜잭션 공부","evidences":[
            {"evidenceId":"11","concept":"격리 수준","observedAt":"2026-11-19T01:00:00Z"},
            {"evidenceId":"12","concept":"MVCC","observedAt":"2026-11-19T01:01:00Z"}]}
          """), body.get());
    } finally {
      server.stop(0);
    }
  }

  @ParameterizedTest
  @CsvSource({"500, true", "502, true", "503, true", "504, true", "429, true",
      "400, false", "401, false", "403, false", "404, false", "422, false"})
  void classifiesStatusWithoutExposingBody(int status, boolean retryable) throws Exception {
    HttpServer server = server(exchange -> new Reply(status, "{\"detail\":\"secret-raw\"}"));
    try {
      var failure = assertThrows(LearningSummaryAiException.class,
          () -> client(server).generate(INPUT, "trace-1234-abcd"));
      assertEquals(retryable, failure.retryable());
      assertFalse(failure.getMessage().contains("secret-raw"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void malformedJsonIsNotRetried() throws Exception {
    HttpServer server = server(exchange -> new Reply(200, "not-json"));
    try {
      assertFalse(assertThrows(LearningSummaryAiException.class,
          () -> client(server).generate(INPUT, "trace-1234-abcd")).retryable());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void unreachableServiceAndMissingConfigAreRetried() throws Exception {
    HttpServer server = server(exchange -> new Reply(200, "{}"));
    var closed = client(server);
    server.stop(0);
    assertTrue(assertThrows(LearningSummaryAiException.class,
        () -> closed.generate(INPUT, "trace-1234-abcd")).retryable());
    assertTrue(assertThrows(LearningSummaryAiException.class,
        () -> new LearningSummaryAiClient(json, "", "service-key")
            .generate(INPUT, "trace-1234-abcd")).retryable());
    assertTrue(assertThrows(LearningSummaryAiException.class,
        () -> new LearningSummaryAiClient(json, "http://127.0.0.1:1", "")
            .generate(INPUT, "trace-1234-abcd")).retryable());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://127.0.0.1", "http://user@127.0.0.1", "http://127.0.0.1/ai",
      "http://127.0.0.1?x=1", "http://127.0.0.1#f"})
  void rejectsNonOriginConfigurationAtStartup(String url) {
    assertThrows(IllegalArgumentException.class,
        () -> new LearningSummaryAiClient(json, url, "service-key"));
  }

  private LearningSummaryAiClient client(HttpServer server) {
    return new LearningSummaryAiClient(json,
        "http://127.0.0.1:" + server.getAddress().getPort(), "service-key");
  }

  private static HttpServer server(Handler handler) throws Exception {
    HttpServer server = HttpServer.create(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      Reply reply = handler.handle(exchange);
      byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(reply.status(), bytes.length);
      try (var output = exchange.getResponseBody()) {
        output.write(bytes);
      }
    });
    server.start();
    return server;
  }

  private interface Handler {
    Reply handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException;
  }

  private record Reply(int status, String body) {}
}
