package com.capstone.backend.analysis.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** 실서버 flag 없이도 readiness의 원문 비노출·전체 deadline을 검증한다. */
class AnalysisAiReadinessTests {
  @Test
  void validHealthAndPrivateInvalidBodiesUseSafeAssertions() throws Exception {
    String privateText = "synthetic-provider-private-value";
    for (String body : new String[] {
        "{\"status\":\"" + privateText + "\",\"analysisEnabled\":true}",
        "{\"status\":\"ready\",\"analysisEnabled\":true,\"modelConnected\":true,"
            + "\"decisionMode\":\"" + privateText + "\"}",
        "{invalid:" + privateText}) {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/health", exchange -> {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      server.start();
      try {
        var failure = assertThrows(AssertionError.class, () -> AnalysisAiLiveTests.requireReadiness(
            "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2)));
        assertFalse(failure.toString().contains(privateText));
        assertNull(failure.getCause());
      } finally {
        server.stop(0);
      }
    }
  }

  @Test
  void headerOnlyStallHasTotalDeadlineAndClosesPeer() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        var executor = Executors.newSingleThreadExecutor()) {
      server.setSoTimeout(3000);
      var sent = new CountDownLatch(1);
      final var peerClosed = executor.submit(() -> {
        try (var socket = server.accept()) {
          socket.setSoTimeout(3000);
          var input = socket.getInputStream();
          StringBuilder header = new StringBuilder();
          while (!header.toString().endsWith("\r\n\r\n")) {
            int value = input.read();
            if (value < 0 || header.length() > 16384) {
              return false;
            }
            header.append((char) value);
          }
          socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 1024\r\n"
              + "Content-Type: application/json\r\n\r\n{").getBytes(StandardCharsets.US_ASCII));
          socket.getOutputStream().flush();
          sent.countDown();
          try {
            return input.read() == -1;
          } catch (java.net.SocketException reset) {
            return true;
          }
        }
      });
      long start = System.nanoTime();
      assertThrows(AssertionError.class, () -> AnalysisAiLiveTests.requireReadiness(
          "http://127.0.0.1:" + server.getLocalPort(), Duration.ofMillis(300)));
      assertTrue(sent.await(1, TimeUnit.SECONDS));
      assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2));
      assertTrue(peerClosed.get(2, TimeUnit.SECONDS));
    }
  }

  @Test
  void actualReadyHealthPasses() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/health", exchange -> {
      byte[] bytes = ("{\"status\":\"ready\",\"analysisEnabled\":true,"
          + "\"modelConnected\":true,\"decisionMode\":\"candidate\"}")
          .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    try {
      AnalysisAiLiveTests.requireReadiness("http://127.0.0.1:" + server.getAddress().getPort(),
          Duration.ofSeconds(2));
    } finally {
      server.stop(0);
    }
  }
}
