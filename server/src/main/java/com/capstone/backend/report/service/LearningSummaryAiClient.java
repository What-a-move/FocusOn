package com.capstone.backend.report.service;

import com.capstone.backend.report.dto.LearningSummaryResponse.KeyConcept;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 내부 학습 정리 API를 호출한다. 모델 호출은 AI가 담당하고 서버는 고정 입력만 보낸다.
 * 분석 AI와 같은 서비스 origin·서버 전용 키를 사용한다. 내부 경로·형식은 AI 팀 합의 전 제안이다.
 */
@Component
public class LearningSummaryAiClient {
  static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final int MAX_RESPONSE_BYTES = 16_384;
  private final ObjectMapper mapper;
  private final URI endpoint;
  private final String key;
  private final HttpClient http;

  /** 설정이 비어 있으면 호출하지 않고 실패로 처리한다. 잘못된 주소는 시작 시 거절한다. */
  public LearningSummaryAiClient(ObjectMapper mapper,
      @Value("${focuson.analysis.ai-url:}") String baseUrl,
      @Value("${focuson.analysis.api-key:}") String key) {
    this.mapper = mapper;
    this.key = key;
    this.endpoint = endpoint(baseUrl);
    // uvicorn은 h2c 업그레이드 요청의 본문을 버리므로 HTTP/1.1로 고정한다.
    this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  /** 근거 한 건의 내부 전송 형식이다. 원문·URL은 담지 않는다. */
  public record Evidence(String evidenceId, String conceptLabel, String evidenceType,
      String observedAt) {}

  /** AI가 돌려준 정리 후보다. 저장 전 서버가 다시 검증한다. */
  public record Draft(String overview, List<KeyConcept> keyConcepts, String nextStartPrompt,
      String modelVersion) {}

  /** 호출·응답 실패는 원문 없이 이 예외로만 알린다. 테스트 대역도 같은 코드를 쓴다. */
  public static final class AiFailure extends RuntimeException {
    private final String code;

    /** 원문 없이 고정 오류 코드만 담는다. */
    public AiFailure(String code) {
      super("Learning summary AI failed");
      this.code = code;
    }

    /** 저장할 고정 오류 코드다. */
    public String code() {
      return code;
    }
  }

  /** 20초 안에 한 번만 호출한다. 재시도는 worker가 1분·5분 간격으로 담당한다. */
  public Draft generate(String requestId, long sessionId, String goalSnapshot,
      List<Evidence> evidences, String traceId) {
    if (endpoint == null || key.isBlank()) {
      throw new AiFailure("AI_NOT_CONFIGURED");
    }
    var body = Map.of("requestId", requestId, "sessionId", Long.toString(sessionId),
        "goalSnapshot", goalSnapshot, "reviewEvidence", evidences);
    var builder = HttpRequest.newBuilder(endpoint).timeout(TIMEOUT)
        .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),
            StandardCharsets.UTF_8));
    if (traceId != null) {
      builder.header("X-Trace-Id", traceId);
    }
    HttpResponse<java.io.InputStream> response;
    try {
      response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    } catch (java.net.http.HttpTimeoutException exception) {
      throw new AiFailure("AI_TIMEOUT");
    } catch (java.io.IOException exception) {
      throw new AiFailure("AI_UNAVAILABLE");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AiFailure("AI_UNAVAILABLE");
    }
    try (var stream = response.body()) {
      byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
      if (response.statusCode() != 200) {
        throw new AiFailure("AI_STATUS");
      }
      if (bytes.length > MAX_RESPONSE_BYTES) {
        throw new AiFailure("AI_INVALID_RESPONSE");
      }
      return parse(mapper.readTree(bytes));
    } catch (java.io.IOException | RuntimeException exception) {
      if (exception instanceof AiFailure failure) {
        throw failure;
      }
      // 응답·파서 예외 메시지에 모델 출력이 섞일 수 있어 고정 코드만 남긴다.
      throw new AiFailure("AI_INVALID_RESPONSE");
    }
  }

  private static Draft parse(JsonNode value) {
    JsonNode root = value.has("data") ? value.path("data") : value;
    if (!root.path("overview").isString() || !root.path("nextStartPrompt").isString()
        || !root.path("keyConcepts").isArray()) {
      throw new AiFailure("AI_INVALID_RESPONSE");
    }
    List<KeyConcept> concepts = new ArrayList<>();
    for (JsonNode item : root.path("keyConcepts")) {
      if (!item.path("name").isString() || !item.path("briefExplanation").isString()
          || !(item.path("sessionEvidence").isString() || item.path("sessionEvidence").isNull()
              || item.path("sessionEvidence").isMissingNode())) {
        throw new AiFailure("AI_INVALID_RESPONSE");
      }
      concepts.add(new KeyConcept(item.path("name").asString(),
          item.path("briefExplanation").asString(),
          item.path("sessionEvidence").isString() ? item.path("sessionEvidence").asString()
              : null));
    }
    String model = root.path("modelVersion").isString() ? root.path("modelVersion").asString()
        : null;
    return new Draft(root.path("overview").asString(), concepts,
        root.path("nextStartPrompt").asString(), model);
  }

  private static URI endpoint(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return null;
    }
    URI base = URI.create(baseUrl);
    if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
        || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
        || base.getFragment() != null
        || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
      throw new IllegalArgumentException("AI_ANALYSIS_URL must be an HTTP service origin");
    }
    return base.resolve("/internal/v1/learning-summaries");
  }
}
