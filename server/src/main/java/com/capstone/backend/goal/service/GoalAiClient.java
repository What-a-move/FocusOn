package com.capstone.backend.goal.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 인증 계약이 정해지기 전 로컬 AI 내부 API와 통신한다. */
@Component
public class GoalAiClient {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
  /** 목표 저장 응답이 기다리는 세분화 요청은 짧게 끊는다. 실패해도 목표는 저장된다. */
  private static final Duration PROFILE_TIMEOUT = Duration.ofSeconds(8);
  private static final String CLARIFY_PATH = "/internal/v1/goals/clarify";
  private static final String PROFILE_PATH = "/internal/v1/goals/profile";
  private static final int MAX_RESPONSE_BYTES = 65_536;
  private static final Set<String> ALLOWED_HOSTS =
      Set.of("localhost", "127.0.0.1", "host.docker.internal");
  private final ObjectMapper mapper;
  private final HttpClient client;
  private final String baseUrl;
  private final String key;

  /** 로컬 서버 주소와 분석 AI와 공유하는 서버 전용 키를 환경 설정에서만 읽는다. */
  public GoalAiClient(ObjectMapper mapper, @Value("${focuson.goal-ai.base-url:}") String baseUrl,
      @Value("${focuson.goal-ai.api-key:}") String key) {
    this.mapper = mapper;
    this.baseUrl = baseUrl;
    this.key = key;
    // 기본 HTTP/2 업그레이드(Upgrade: h2c)를 보내면 uvicorn이 본문을 버리고 422로 응답한다.
    this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(CONNECT_TIMEOUT).build();
  }

  /** 내부 계약의 요청을 전송하고 결과를 파싱한다. 재시도는 호출 서비스가 담당한다. */
  public JsonNode clarify(Map<String, Object> body) {
    return send(CLARIFY_PATH, REQUEST_TIMEOUT, body);
  }

  /** 목표 한 줄을 세분화(GoalProfile)한다(#314). 재시도하지 않는다. */
  public JsonNode profile(Map<String, Object> body) {
    return send(PROFILE_PATH, PROFILE_TIMEOUT, body);
  }

  private JsonNode send(String path, Duration timeout, Map<String, Object> body) {
    if (key.isBlank()) {
      throw new GoalAiException(Failure.UNAVAILABLE, false);
    }
    URI endpoint = endpoint(path);
    var builder = HttpRequest.newBuilder(endpoint).timeout(timeout)
        .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),
            StandardCharsets.UTF_8));
    // 같은 요청의 traceId를 AI까지 넘겨 모듈 경계 로그를 잇는다(ARCHITECTURE 로그·추적 원칙).
    String trace = MDC.get("traceId");
    if (trace != null && trace.matches("[A-Za-z0-9-]{8,64}")) {
      builder.header("X-Trace-Id", trace);
    }
    // 요청 timeout은 응답 헤더까지만 막으므로 본문 지연까지 포함한 전체 마감을 따로 둔다.
    var pending = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    try {
      var response = pending.get(timeout.toMillis() + 1000, TimeUnit.MILLISECONDS);
      byte[] bytes = response.body();
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
    } catch (TimeoutException exception) {
      throw new GoalAiException(Failure.TIMEOUT, true);
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof HttpTimeoutException) {
        throw new GoalAiException(Failure.TIMEOUT, true);
      }
      throw new GoalAiException(Failure.UNAVAILABLE, true);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new GoalAiException(Failure.UNAVAILABLE, false);
    } finally {
      if (!pending.isDone()) {
        pending.cancel(true);
      }
    }
  }

  /**
   * AI 주소는 SSRF를 막기 위해 고정된 호스트 이름만 허용한다. 로컬 실행은 루프백, Docker 실행은
   * compose의 {@code extra_hosts}로 호스트 게이트웨이에 연결되는 {@code host.docker.internal}이다.
   */
  URI endpoint() {
    return endpoint(CLARIFY_PATH);
  }

  URI endpoint(String path) {
    try {
      URI base = URI.create(baseUrl);
      if (!"http".equals(base.getScheme()) || base.getUserInfo() != null
          || !ALLOWED_HOSTS.contains(base.getHost())
          || base.getQuery() != null || base.getFragment() != null
          || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
        throw new IllegalArgumentException();
      }
      return base.resolve(path);
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
