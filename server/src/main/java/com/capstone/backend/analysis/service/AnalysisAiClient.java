package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.service.AnalysisRunStore.Prepared;
import com.capstone.backend.common.api.SafeApiLog;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 고정 서비스 주소만 호출하며 외부 원문·예외를 공개 응답이나 로그에 넣지 않는다. */
@Component
public class AnalysisAiClient {
  private final ObjectMapper mapper;
  private final URI endpoint;
  private final String key;
  private final HttpClient http;

  /** 비어 있는 설정은 fallback을 허용하되 잘못된 주소 설정은 시작 시 거절한다. */
  public AnalysisAiClient(ObjectMapper mapper,
      @Value("${focuson.analysis.ai-url:}") String baseUrl,
      @Value("${focuson.analysis.api-key:}") String key) {
    this.mapper = mapper;
    this.key = key;
    this.endpoint = endpoint(baseUrl);
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }

  /** 제한된 시간·응답 크기 안에서 AI 판정을 받아 정규화한다. */
  public AnalysisDecision analyze(Prepared prepared, Request request, long userId) {
    long start = System.nanoTime();
    if (endpoint == null || key.isBlank() || !prepared.latest()) {
      SafeApiLog.analysisEvent(SafeApiLog.AnalysisEvent.AI_FAIL, start);
      return AnalysisDecision.unavailable();
    }
    SafeApiLog.analysisEvent(SafeApiLog.AnalysisEvent.AI_START, start);
    CompletableFuture<HttpResponse<byte[]>> pending = null;
    try {
      var builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
          .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(
              envelope(prepared, request, userId))));
      String trace = MDC.get("traceId");
      if (trace != null && trace.matches("[A-Za-z0-9-]{8,64}")) {
        builder.header("X-Trace-Id", trace);
      }
      // request timeout만으로 본문 stall을 제한할 수 없어 전체 future에 별도 deadline을 둔다.
      pending = http.sendAsync(builder.build(), ignored ->
          HttpResponse.BodySubscribers.mapping(
              HttpResponse.BodySubscribers.limiting(HttpResponse.BodySubscribers.ofByteArray(),
                  8192), value -> value));
      var response = pending.get(5, TimeUnit.SECONDS);
      if (response.statusCode() != 200) {
        throw new IllegalStateException("AI_STATUS");
      }
      AnalysisDecision decision = normalize(mapper.readTree(response.body()));
      SafeApiLog.analysisEvent(decision.status() == AnalysisStatus.ERROR
          ? SafeApiLog.AnalysisEvent.AI_FAIL : SafeApiLog.AnalysisEvent.AI_OK, start);
      return decision;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException | RuntimeException exception) {
      // Provider/Jackson 예외 메시지는 입력·서비스 자격증명을 포함할 수 있다.
    } finally {
      if (pending != null && !pending.isDone()) {
        pending.cancel(true);
      }
    }
    SafeApiLog.analysisEvent(SafeApiLog.AnalysisEvent.AI_FAIL, start);
    return AnalysisDecision.unavailable();
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
      throw new IllegalArgumentException("AI_ANALYSIS_URL must be an HTTP service origin");
    }
    return base.resolve("/internal/v1/analysis-runs");
  }

  /** 공개 요청의 관측과 서버가 검증한 세션 snapshot을 분리해 내부 AI 계약을 구성한다. */
  private Map<String, Object> envelope(Prepared p, Request request, long userId) {
    var input = new LinkedHashMap<String, Object>();
    input.put("sessionId", Long.toString(p.sessionId()));
    input.put("clientRunId", request.clientRunId());
    input.put("contentSource", request.contentSource().name());
    input.put("observedAt", request.observedAt().toString());
    input.put("sequence", request.sequence());
    input.put("goalSnapshot", p.goalSnapshot());
    input.put("guardStatus", null);
    if (request.metadata() != null) {
      var meta = new LinkedHashMap<String, Object>();
      var source = request.metadata();
      meta.put("appName", source.appName() == null ? "" : source.appName());
      meta.put("windowTitle", source.windowTitle());
      meta.put("pageTitle", source.pageTitle() == null ? "" : source.pageTitle());
      meta.put("urlHost", AnalysisPrivacy.host(request));
      input.put("metadata", meta);
    }
    if (request.contentPayload() != null) {
      input.put("contentPayload", Map.of("text", request.contentPayload().text()));
    }
    var context = new LinkedHashMap<String, Object>();
    context.put("userId", Long.toString(userId));
    context.put("sessionId", Long.toString(p.sessionId()));
    context.put("runId", Long.toString(p.runId()));
    context.put("goalId", Long.toString(p.goalId()));
    context.put("goalVersion", 1);
    context.put("clientRunId", request.clientRunId());
    context.put("sequence", request.sequence());
    context.put("navigationId", "analysis-" + p.runId());
    context.put("observedAt", request.observedAt().toString());
    context.put("contentSource", request.contentSource().name());
    context.put("isActiveTarget", p.latest());
    context.put("sessionStatus", "RUNNING");
    context.put("exclusionMode", "NONE");
    context.put("resumeRequired", false);
    context.put("policyVersion", p.policyVersion());
    context.put("extractorVersion", "public-text-v1");
    context.put("stateVersion", p.sessionVersion() + 1);
    boolean metadata = request.contentSource() == ContentSource.APP_META
        || request.contentSource() == ContentSource.TAB_META;
    String extraction = metadata || (request.contentPayload() != null
        && !request.contentPayload().text().isBlank()) ? "SUCCESS" : "FAILED";
    return Map.of("request", input, "goal", Map.of("goalId", Long.toString(p.goalId()),
        "goalVersion", 1, "text", p.goalSnapshot(), "confirmed", true),
        "context", context, "extractionStatus", extraction);
  }

  /** AI schema 밖 값과 receipt는 정상 판정으로 취급하지 않는다. */
  static AnalysisDecision normalize(JsonNode value) {
    try {
      if (!value.isObject() || !value.path("decision").isString()
          || !value.has("confidence") || !value.path("reasonCode").isString()
          || !version(value.path("modelVersion")) || !version(value.path("promptVersion"))) {
        return AnalysisDecision.unavailable();
      }
      var relation = Relation.valueOf(value.path("decision").asString());
      var reason = ReasonCode.valueOf(value.path("reasonCode").asString());
      if (relation == Relation.EXCLUDED || relation == Relation.PRIVACY_BLOCKED) {
        return AnalysisDecision.unavailable();
      }
      var score = value.path("confidence");
      if (!score.isNull() && (!score.isNumber() || !Double.isFinite(score.asDouble())
          || score.asDouble() < 0 || score.asDouble() > 1)) {
        return AnalysisDecision.unavailable();
      }
      Double confidence = score.isNull() ? null : score.asDouble();
      String model = value.path("modelVersion").asString();
      String prompt = value.path("promptVersion").asString();
      if (relation == Relation.UNCERTAIN) {
        if (reason == ReasonCode.SEMANTIC_GOAL_MATCH
            || reason == ReasonCode.SEMANTIC_GOAL_MISMATCH) {
          return AnalysisDecision.unavailable();
        }
        return new AnalysisDecision(relation, null, reason,
            reason == ReasonCode.ANALYSIS_UNAVAILABLE ? AnalysisStatus.ERROR
                : AnalysisStatus.NEEDS_CONFIRMATION, model, prompt);
      }
      if (reason != (relation == Relation.RELATED ? ReasonCode.SEMANTIC_GOAL_MATCH
          : ReasonCode.SEMANTIC_GOAL_MISMATCH)) {
        return AnalysisDecision.unavailable();
      }
      // 최신 Notion의 베타 예외: 수치 confidence를 지어내지 않고 버전을 함께 보존한다.
      boolean candidate = prompt.endsWith("-uncalibrated") && confidence == null;
      if (!candidate && (confidence == null || confidence < .6)) {
        return new AnalysisDecision(Relation.UNCERTAIN, null, ReasonCode.INSUFFICIENT_CONTEXT,
            AnalysisStatus.NEEDS_CONFIRMATION, model, prompt);
      }
      return new AnalysisDecision(relation, confidence, reason, AnalysisStatus.COMPLETED,
          model, prompt);
    } catch (IllegalArgumentException exception) {
      return AnalysisDecision.unavailable();
    }
  }

  /** 버전은 원문 대신 남기는 고정 길이 식별자이므로 임의의 자유 텍스트를 받지 않는다. */
  private static boolean version(JsonNode value) {
    return value.isString() && value.asString().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
  }
}
