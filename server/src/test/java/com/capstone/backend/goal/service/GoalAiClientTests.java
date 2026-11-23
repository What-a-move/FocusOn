package com.capstone.backend.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 루프백 AI stub으로 목표 확인 요청의 전송 형식을 검증한다. */
class GoalAiClientTests {
  /** uvicorn은 Upgrade 헤더가 있으면 본문을 버리므로 HTTP/1.1 평문으로만 보내야 한다. */
  @Test
  void sendsPlainHttp11JsonBodyWithoutUpgradeHeaders() throws Exception {
    AtomicReference<String> upgrade = new AtomicReference<>("unset");
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(
        InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/internal/v1/goals/clarify", exchange -> {
      upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = "{\"data\":{\"clarityStatus\":\"CLEAR\"}}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      var client = new GoalAiClient(new ObjectMapper(),
          "http://127.0.0.1:" + server.getAddress().getPort(), "server-only-key");
      var result = client.clarify(Map.of("requestId", "r1", "goalText", "토익 점수 올리기"));
      assertEquals("CLEAR", result.path("data").path("clarityStatus").asText());
      assertNull(upgrade.get());
      assertEquals("Bearer server-only-key", authorization.get());
      assertTrue(body.get().contains("토익 점수 올리기"));
    } finally {
      server.stop(0);
    }
  }

  /** 세분화 요청은 profile 경로로 보낸다(#314). */
  @Test
  void profileUsesItsOwnInternalPath() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(
        InetAddress.getLoopbackAddress(), 0), 0);
    AtomicReference<String> trace = new AtomicReference<>();
    server.createContext("/internal/v1/goals/profile", exchange -> {
      trace.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = "{\"data\":{\"goalProfile\":{\"mainTopic\":\"React\"}}}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      var client = new GoalAiClient(new ObjectMapper(),
          "http://127.0.0.1:" + server.getAddress().getPort(), "server-only-key");
      org.slf4j.MDC.put("traceId", "trace-goal-profile-1");
      var result = client.profile(Map.of("requestId", "r1", "goalText", "리액트 공부"));
      assertEquals("React", result.path("data").path("goalProfile").path("mainTopic").asText());
      assertTrue(body.get().contains("리액트 공부"));
      assertEquals("trace-goal-profile-1", trace.get());
    } finally {
      org.slf4j.MDC.remove("traceId");
      server.stop(0);
    }
  }

  /** 헤더만 보내고 본문을 멈춘 AI도 전체 마감 안에 시간 초과로 끝난다. */
  @Test
  void stalledProfileBodyEndsWithinDeadline() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(
        InetAddress.getLoopbackAddress(), 0), 0);
    var release = new java.util.concurrent.CountDownLatch(1);
    server.createContext("/internal/v1/goals/profile", exchange -> {
      exchange.sendResponseHeaders(200, 100);
      exchange.getResponseBody().write("{".getBytes(StandardCharsets.UTF_8));
      exchange.getResponseBody().flush();
      try {
        release.await(30, java.util.concurrent.TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
      exchange.close();
    });
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
    try {
      var client = new GoalAiClient(new ObjectMapper(),
          "http://127.0.0.1:" + server.getAddress().getPort(), "server-only-key");
      long started = System.nanoTime();
      var failure = assertThrows(GoalAiClient.GoalAiException.class,
          () -> client.profile(Map.of("requestId", "r1", "goalText", "리액트 공부")));
      assertEquals(GoalAiClient.Failure.TIMEOUT, failure.failure());
      assertTrue(System.nanoTime() - started
          < java.util.concurrent.TimeUnit.SECONDS.toNanos(15));
    } finally {
      release.countDown();
      server.stop(0);
    }
  }

  /** 분석 AI와 공유하는 서버 전용 키가 없으면 요청을 보내지 않고 안전하게 실패한다. */
  @Test
  void rejectsBeforeSendingWhenApiKeyIsBlank() {
    var client = new GoalAiClient(new ObjectMapper(), "http://127.0.0.1:8000", "");
    var exception = assertThrows(GoalAiClient.GoalAiException.class,
        () -> client.clarify(Map.of("requestId", "r1", "goalText", "토익 점수 올리기")));
    assertEquals(GoalAiClient.Failure.UNAVAILABLE, exception.failure());
    assertTrue(!exception.retryable());
  }

  /** Docker 실행용 호스트 게이트웨이 이름은 허용하고 그 밖의 호스트는 요청 전에 거절한다. */
  @Test
  void allowsOnlyLoopbackAndDockerHostGatewayNames() {
    var docker = new GoalAiClient(new ObjectMapper(), "http://host.docker.internal:8000", "key");
    assertEquals("http://host.docker.internal:8000/internal/v1/goals/clarify",
        docker.endpoint().toString());
    for (String blocked : new String[] {"http://example.com:8000", "http://10.0.0.5:8000",
        "http://host.docker.internal.evil.com:8000", ""}) {
      var client = new GoalAiClient(new ObjectMapper(), blocked, "key");
      assertThrows(GoalAiClient.GoalAiException.class, client::endpoint, blocked);
    }
  }
}
