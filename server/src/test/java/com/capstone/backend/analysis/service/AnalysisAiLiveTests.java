package com.capstone.backend.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.capstone.backend.analysis.dto.AnalysisDtos.ContentPayload;
import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.Metadata;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.service.AnalysisRunStore.Prepared;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

/** 명시적으로 켠 경우만 실제 Backend client→AI→모델을 호출한다. DB/앱 QA와 구분한다. */
@EnabledIfEnvironmentVariable(named = "FOCUSON_LIVE_AI_TEST", matches = "true")
class AnalysisAiLiveTests {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String GOAL = "Spring Security JWT 인증 구현 학습";

  @BeforeAll
  static void verifyRealModelReadiness() throws Exception {
    String origin = System.getenv("AI_ANALYSIS_URL");
    String key = System.getenv("FOCUSON_AI_API_KEY");
    assertNotNull(origin, "실제 AI 서비스 origin 설정이 필요합니다.");
    assertNotNull(key, "서비스 키는 환경 변수로 설정해 주세요. 값을 출력하지 않습니다.");
    assertFalse(origin.isBlank());
    assertFalse(key.isBlank());
    // 실제 client와 동일한 origin 검증을 health 요청보다 먼저 수행한다.
    new AnalysisAiClient(JSON, origin, key);
    requireReadiness(origin, Duration.ofSeconds(5));
  }

  static void requireReadiness(String origin, Duration deadline) {
    CompletableFuture<HttpResponse<byte[]>> pending = null;
    try {
      pending = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
          .followRedirects(HttpClient.Redirect.NEVER).build().sendAsync(
              HttpRequest.newBuilder(URI.create(origin).resolve("/health"))
                  .timeout(deadline).GET().build(),
              ignored -> HttpResponse.BodySubscribers.limiting(
                  HttpResponse.BodySubscribers.ofByteArray(), 8192));
      var response = pending.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
      assertEquals(200, response.statusCode(), "AI 모델 readiness가 확인되지 않았습니다.");
      var health = JSON.readTree(response.body());
      // 외부 문자열을 assertEquals의 actual에 넣으면 실패 보고서에 원문이 저장된다.
      assertTrue("ready".equals(health.path("status").asString()), "AI 상태가 ready가 아닙니다.");
      assertTrue(health.path("analysisEnabled").isBoolean()
          && health.path("analysisEnabled").asBoolean(), "AI 분석 준비가 확인되지 않았습니다.");
      assertTrue(health.path("modelConnected").isBoolean()
          && health.path("modelConnected").asBoolean(), "실제 모델 연결이 확인되지 않았습니다.");
      assertTrue("candidate".equals(health.path("decisionMode").asString()),
          "AI candidate 모드가 확인되지 않았습니다.");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      fail("AI readiness 검사가 중단됐습니다.");
    } catch (ExecutionException | TimeoutException | RuntimeException exception) {
      // JSON/HTTP 예외는 원문을 포함할 수 있어 원인 예외도 테스트 보고서에 연결하지 않는다.
      fail("AI readiness 응답을 제한 시간 안에 안전하게 검증하지 못했습니다.");
    } finally {
      if (pending != null && !pending.isDone()) {
        pending.cancel(true);
      }
    }
  }

  @Test
  void actualRelatedDecisionThroughBackendClient() {
    verify(Relation.RELATED,
        "Spring Security JWT 서명 검증과 토큰 만료 시간 검사, 인증 필터 구현을 학습합니다.");
  }

  @Test
  void actualUnrelatedDecisionThroughBackendClient() {
    verify(Relation.UNRELATED,
        "오늘 축구 경기 하이라이트와 선수 득점 순위, 게임 방송과 예능 영상을 시청합니다.");
  }

  private void verify(Relation expected, String syntheticText) {
    var client = new AnalysisAiClient(JSON, System.getenv("AI_ANALYSIS_URL"),
        System.getenv("FOCUSON_AI_API_KEY"));
    Instant now = Instant.now();
    String runId = UUID.randomUUID().toString();
    var request = new Request(runId, ContentSource.CHROME_DOM, now, BigDecimal.ONE, null,
        new Metadata("Google Chrome", null, "Synthetic QA", "example.com"),
        new ContentPayload(syntheticText));
    var prepared = new Prepared(1, 1, 1, 0, now, GOAL, "v1", null, true, null);
    MDC.put("traceId", runId);
    try {
      var result = client.analyze(prepared, request, 1);
      assertEquals(expected, result.relation(), "실제 모델 합성 시나리오 판정은 아직 확인되지 않았습니다.");
      assertTrue(result.confidence() != null && result.confidence() >= .6
          || result.confidence() == null && result.promptVersion().endsWith("-uncalibrated"));
    } finally {
      MDC.remove("traceId");
    }
  }
}
