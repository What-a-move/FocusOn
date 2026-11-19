package com.capstone.backend.report.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 내부 API {@code POST /internal/v1/learning-summaries}를 호출한다. 요청·응답 형식은 AI 팀과
 * 합의 전인 서버 제안안이다(#288). 재시도와 결과 검증은 호출하는 Worker가 담당한다.
 */
@Component
public class LearningSummaryAiClient {
  /** 이슈 #288·AI PLAN의 생성 모델 제한 시간이다. 본문 수신까지 포함한 전체 deadline이다. */
  static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
  private static final int MAX_RESPONSE_BYTES = 16_384;
  private final ObjectMapper mapper;
  private final URI endpoint;
  private final String key;
  private final HttpClient http;

  /** 비어 있는 설정은 호출 불가로 처리하고, 잘못된 주소 설정은 시작 시 거절한다. */
  public LearningSummaryAiClient(ObjectMapper mapper,
      @Value("${focuson.learning-summary.ai-url:}") String baseUrl,
      @Value("${focuson.learning-summary.api-key:}") String key) {
    this.mapper = mapper;
    this.key = key;
    this.endpoint = endpoint(baseUrl);
    // uvicorn은 h2c 업그레이드 요청의 본문을 버리므로 HTTP/1.1로 고정한다(GoalAiClient와 같은 이유).
    this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  /** 고정된 입력으로 정리 후보를 요청한다. 실패는 원문 없이 재시도 가능 여부만 알린다. */
  public JsonNode generate(Input input, String traceId) {
    if (endpoint == null || key.isBlank()) {
      // 설정 누락은 배포 중 일시적일 수 있어 재시도 대상으로 둔다. 재시도를 다 쓰면 FAILED다.
      throw new LearningSummaryAiException(true);
    }
    var request = HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT)
        .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
        .header("X-Trace-Id", traceId)
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body(input))))
        .build();
    CompletableFuture<HttpResponse<byte[]>> pending = null;
    try {
      // request timeout은 헤더 수신까지만 제한하므로 본문 지연까지 막도록 future에 deadline을 둔다.
      pending = http.sendAsync(request, ignored -> HttpResponse.BodySubscribers.limiting(
          HttpResponse.BodySubscribers.ofByteArray(), MAX_RESPONSE_BYTES));
      var response = pending.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      int status = response.statusCode();
      if (status == 429 || status >= 500) {
        throw new LearningSummaryAiException(true);
      }
      if (status != 200) {
        // 인증·계약 불일치는 같은 요청을 다시 보내도 결과가 바뀌지 않는다.
        throw new LearningSummaryAiException(false);
      }
      try {
        return mapper.readTree(response.body());
      } catch (RuntimeException exception) {
        throw new LearningSummaryAiException(false);
      }
    } catch (TimeoutException exception) {
      throw new LearningSummaryAiException(true);
    } catch (ExecutionException exception) {
      // 연결 실패·응답 크기 초과를 포함한다. 원인 메시지는 입력이나 키를 담을 수 있어 버린다.
      throw new LearningSummaryAiException(true);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new LearningSummaryAiException(true);
    } finally {
      if (pending != null && !pending.isDone()) {
        pending.cancel(true);
      }
    }
  }

  private Map<String, Object> body(Input input) {
    return Map.of("sessionId", Long.toString(input.sessionId()),
        "goalSnapshot", input.goalSnapshot(),
        "evidences", input.evidences().stream().map(evidence -> Map.of(
            "evidenceId", Long.toString(evidence.evidenceId()),
            "concept", evidence.concept(),
            "observedAt", evidence.observedAt())).toList());
  }

  /** 클라이언트 입력이 아닌 서버 설정의 서비스 origin만 내부 호출 대상으로 허용한다. */
  private static URI endpoint(String baseUrl) {
    if (baseUrl.isBlank()) {
      return null;
    }
    URI base = URI.create(baseUrl);
    if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
        || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
        || base.getFragment() != null
        || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
      throw new IllegalArgumentException("AI_LEARNING_SUMMARY_URL must be an HTTP service origin");
    }
    return base.resolve("/internal/v1/learning-summaries");
  }

  /** 입력 마감 시점에 고정한 목표와 근거다. 근거는 120자 이하 개념만 담는다. */
  public record Input(long sessionId, String goalSnapshot, List<Evidence> evidences) {}

  /** 모델이 결과를 근거에 연결할 수 있도록 ID와 관측 시각을 함께 보낸다. */
  public record Evidence(long evidenceId, String concept, String observedAt) {}

  /** 외부 실패를 원문 없이 재시도 가능 여부로만 분류한다. */
  public static final class LearningSummaryAiException extends RuntimeException {
    private final boolean retryable;

    LearningSummaryAiException(boolean retryable) {
      super("Learning summary AI request failed");
      this.retryable = retryable;
    }

    /** 같은 입력으로 나중에 다시 보내면 성공할 수 있는지 반환한다. */
    public boolean retryable() {
      return retryable;
    }
  }
}
