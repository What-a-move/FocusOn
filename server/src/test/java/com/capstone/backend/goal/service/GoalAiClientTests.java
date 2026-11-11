package com.capstone.backend.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    AtomicReference<String> body = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(
        InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/internal/v1/goals/clarify", exchange -> {
      upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
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
          "http://127.0.0.1:" + server.getAddress().getPort());
      var result = client.clarify(Map.of("requestId", "r1", "goalText", "토익 점수 올리기"));
      assertEquals("CLEAR", result.path("data").path("clarityStatus").asText());
      assertNull(upgrade.get());
      assertTrue(body.get().contains("토익 점수 올리기"));
    } finally {
      server.stop(0);
    }
  }
}
