package com.capstone.backend.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 마감 시간이 헤더뿐 아니라 응답 본문 읽기까지 적용되는지 실제 HTTP로 확인한다. */
class LearningSummaryAiClientTests {
  private static final String PATH = "/internal/v1/learning-summaries";
  private static final Duration TIMEOUT = Duration.ofSeconds(1);
  private final CountDownLatch release = new CountDownLatch(1);
  private HttpServer server;
  private LearningSummaryAiClient client;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    client = new LearningSummaryAiClient(JsonMapper.builder().build(),
        "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", TIMEOUT);
  }

  @AfterEach
  void stop() {
    release.countDown();
    server.stop(0);
  }

  @Test
  void completeResponseIsParsed() {
    respond(exchange -> {
      byte[] body = ("{\"overview\":\"정리\",\"keyConcepts\":[{\"name\":\"MVCC\","
          + "\"briefExplanation\":\"설명\"}],\"nextStartPrompt\":\"다음\","
          + "\"modelVersion\":\"summary-v1\"}").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    var draft = generate();
    assertEquals("정리", draft.overview());
    assertEquals("summary-v1", draft.modelVersion());
  }

  @Test
  void stalledBodyAfterHeadersTimesOut() {
    respond(exchange -> {
      exchange.sendResponseHeaders(200, 0);
      exchange.getResponseBody().write("{\"overview\":".getBytes(StandardCharsets.UTF_8));
      exchange.getResponseBody().flush();
      awaitRelease();
      exchange.close();
    });
    assertTimesOutWithinDeadline();
  }

  @Test
  void missingHeadersTimesOut() {
    respond(exchange -> {
      awaitRelease();
      exchange.close();
    });
    assertTimesOutWithinDeadline();
  }

  private void assertTimesOutWithinDeadline() {
    long started = System.nanoTime();
    var failure = assertThrows(LearningSummaryAiClient.AiFailure.class, this::generate);
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    assertEquals("AI_TIMEOUT", failure.code());
    assertTrue(elapsed < TIMEOUT.toMillis() + 3000, "took " + elapsed + "ms");
  }

  private LearningSummaryAiClient.Draft generate() {
    return client.generate("1-1", 1L, "목표", List.of(new LearningSummaryAiClient.Evidence("1",
        "개념", "CONCEPT", "2026-11-21T00:00:00Z")), null);
  }

  private void respond(com.sun.net.httpserver.HttpHandler handler) {
    server.createContext(PATH, exchange -> {
      exchange.getRequestBody().readAllBytes();
      handler.handle(exchange);
    });
  }

  private void awaitRelease() {
    try {
      release.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
