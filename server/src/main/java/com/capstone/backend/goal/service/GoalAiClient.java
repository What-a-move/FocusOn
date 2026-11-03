package com.capstone.backend.goal.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 인증 계약이 정해지기 전 로컬 AI 내부 API와 통신한다. */
@Component
public class GoalAiClient {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
  private static final int MAX_RESPONSE_BYTES = 65_536;
  private final ObjectMapper mapper;
  private final HttpClient client;
  private final String baseUrl;

  /** 로컬 서버 주소는 환경 설정에서만 읽는다. */
  public GoalAiClient(ObjectMapper mapper, @Value("${focuson.goal-ai.base-url:}") String baseUrl) {
    this.mapper = mapper;
    this.baseUrl = baseUrl;
    this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
  }

  /** 내부 계약의 요청을 전송하고 결과를 파싱한다. 재시도는 호출 서비스가 담당한다. */
  public JsonNode clarify(Map<String, Object> body) {
    URI endpoint = endpoint();
    var request = HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),
            StandardCharsets.UTF_8)).build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
          throw new GoalAiException(Failure.INVALID_RESPONSE, false);
        }
        if (response.statusCode() == 504) {
          throw new GoalAiException(Failure.TIMEOUT, true);
        }
        if (response.statusCode() == 502 || response.statusCode() == 503) {
          boolean retryable;
          try {
            retryable = mapper.readTree(bytes).path("retryable").asBoolean(false);
          } catch (RuntimeException exception) {
            retryable = false;
          }
          throw new GoalAiException(Failure.UNAVAILABLE, retryable);
        }
        if (response.statusCode() != 200) {
          throw new GoalAiException(Failure.INVALID_RESPONSE, false);
        }
        try {
          return mapper.readTree(bytes);
        } catch (RuntimeException exception) {
          throw new GoalAiException(Failure.INVALID_RESPONSE, false);
        }
      }
    } catch (HttpTimeoutException exception) {
      throw new GoalAiException(Failure.TIMEOUT, true);
    } catch (ConnectException exception) {
      throw new GoalAiException(Failure.UNAVAILABLE, true);
    } catch (IOException exception) {
      throw new GoalAiException(Failure.UNAVAILABLE, true);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new GoalAiException(Failure.UNAVAILABLE, false);
    }
  }

  private URI endpoint() {
    try {
      URI base = URI.create(baseUrl);
      if (!"http".equals(base.getScheme()) || base.getUserInfo() != null
          || !("localhost".equals(base.getHost()) || "127.0.0.1".equals(base.getHost()))
          || base.getQuery() != null || base.getFragment() != null
          || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
        throw new IllegalArgumentException();
      }
      return base.resolve("/internal/v1/goals/clarify");
    } catch (IllegalArgumentException exception) {
      throw new GoalAiException(Failure.UNAVAILABLE, false);
    }
  }

  /** 외부 실패를 원문 없이 분류한다. */
  public enum Failure { UNAVAILABLE, TIMEOUT, INVALID_RESPONSE }

  /** AI 요청의 안전한 실패 정보다. */
  public static final class GoalAiException extends RuntimeException {
    private final Failure failure;
    private final boolean retryable;

    private GoalAiException(Failure failure, boolean retryable) {
      super("Goal AI request failed");
      this.failure = failure;
      this.retryable = retryable;
    }

    /** 호출 실패 유형을 반환한다. */
    public Failure failure() {
      return failure;
    }

    /** 동일 요청 재시도가 가능한지 반환한다. */
    public boolean retryable() {
      return retryable;
    }
  }
}
