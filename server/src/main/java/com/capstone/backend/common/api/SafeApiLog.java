package com.capstone.backend.common.api;

import java.math.BigDecimal;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/** 허용한 이벤트와 메타데이터만 로그로 내보내는 API 로거다. */
public final class SafeApiLog {
  private static final Logger LOGGER = LoggerFactory.getLogger(SafeApiLog.class);
  private static final Set<String> SAFE_SEGMENTS = Set.of(
      "api", "v1", "auth", "google", "exchange", "refresh", "logout", "me", "users",
      "profile", "consents", "focus-settings", "analysis-excluded-domains",
      "onboarding-readiness", "pairing-codes", "extension-pairings", "devices", "goals",
      "clarify", "sessions", "active", "pause", "resume", "extend", "end", "heartbeats",
      "local-events", "analysis-runs", "observation-context", "interventions",
      "acknowledgements", "deliveries", "summary",
      "learning-summary", "learning-summaries", "reports", "calendar", "daily", "weekly");
  private static final Set<String> SAFE_METHODS = Set.of(
      "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

  private SafeApiLog() {}

  /** 분석 경계에서 고정 enum만 받고 원문·외부 예외 메시지를 받지 않는다. */
  public static void analysisEvent(AnalysisEvent event, long startedNanos) {
    analysisRecord(event, startedNanos).log(event.value);
  }

  /** 목표 프로필 호출은 본문과 자격증명 없이 경과와 성공 여부만 남긴다. */
  public static void goalProfileEvent(AnalysisEvent event, long startedNanos) {
    var output = event == AnalysisEvent.AI_FAIL ? LOGGER.atWarn() : LOGGER.atInfo();
    output.addKeyValue("component", "goal-profile")
        .addKeyValue("event", event.value)
        .addKeyValue("durMs", Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000))
        .log(event.value);
  }

  /** 정규화된 판정과 상태만 남기며 AI 응답 전문이나 예외를 받지 않는다. */
  public static void analysisAiResult(boolean failed, long startedNanos, Integer upstreamStatus,
      Enum<?> relation, Double confidence, Enum<?> reasonCode) {
    var event = failed ? AnalysisEvent.AI_FAIL : AnalysisEvent.AI_OK;
    var output = analysisDecisionFields(analysisRecord(event, startedNanos), relation,
        confidence, reasonCode);
    if (upstreamStatus != null) {
      output.addKeyValue("upstreamStatus", upstreamStatus);
    }
    if (failed) {
      output.addKeyValue("stage", "ai");
    }
    output.log(event.value);
  }

  /** 저장 트랜잭션에서 확정한 판정만 기록한다. */
  public static void analysisStoreResult(long startedNanos, Enum<?> relation,
      Double confidence, Enum<?> reasonCode) {
    var event = AnalysisEvent.STORE_OK;
    analysisDecisionFields(analysisRecord(event, startedNanos), relation, confidence, reasonCode)
        .log(event.value);
  }

  private static LoggingEventBuilder analysisDecisionFields(LoggingEventBuilder output,
      Enum<?> relation, Double confidence, Enum<?> reasonCode) {
    output.addKeyValue("relation", relation.name());
    if (confidence != null) {
      output.addKeyValue("confidence", confidence);
    }
    if (reasonCode != null) {
      output.addKeyValue("reasonCode", reasonCode.name());
    }
    return output;
  }

  private static LoggingEventBuilder analysisRecord(AnalysisEvent event, long startedNanos) {
    boolean failure = event == AnalysisEvent.AI_FAIL || event == AnalysisEvent.STORE_FAIL;
    var output = failure ? LOGGER.atWarn() : LOGGER.atInfo();
    return output.addKeyValue("component", "analysis-orchestrator")
        .addKeyValue("event", event.value)
        .addKeyValue("durMs", Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000));
  }

  /** 모듈 연동 로깅 정본에서 정한 분석 이벤트다. */
  public enum AnalysisEvent {
    GATE_PASS("gate.rule.pass"), GATE_BLOCK("gate.rule.block"),
    AI_START("ai.call.start"), AI_OK("ai.call.ok"), AI_FAIL("ai.call.fail"),
    STORE_OK("store.write.ok"), STORE_FAIL("store.write.fail");

    private final String value;

    AnalysisEvent(String value) {
      this.value = value;
    }
  }

  /**
   * 학습 정리 worker의 AI 호출·저장 결과를 분석과 같은 정본 이벤트 이름으로 기록한다.
   * 목표·근거·모델 출력·예외 메시지는 넣지 않고 고정 오류 코드만 남긴다.
   */
  public static void learningSummaryEvent(AnalysisEvent event, long startedNanos,
      String errorCode) {
    boolean failure = event == AnalysisEvent.AI_FAIL || event == AnalysisEvent.STORE_FAIL;
    var output = failure ? LOGGER.atWarn() : LOGGER.atInfo();
    output = output.addKeyValue("component", "learning-summary-worker")
        .addKeyValue("event", event.value)
        .addKeyValue("durMs", Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000));
    if (errorCode != null) {
      output = output.addKeyValue("errorCode", errorCode);
    }
    output.log(event.value);
  }

  /** 분석 기록 정리 실패에서 데이터와 식별자를 제외한다. */
  public static void analysisRetentionFailure(RuntimeException exception) {
    LOGGER.atWarn().addKeyValue("component", "analysis-run-retention")
        .addKeyValue("errorClass", exception.getClass().getSimpleName())
        .log("analysis run retention failed");
  }

  /** 보관 기간 정리 실패를 식별자와 예외 메시지 없이 기록한다. */
  public static void tokenRetentionFailure(RuntimeException exception) {
    LOGGER.atWarn().addKeyValue("component", "token-family-retention")
        .addKeyValue("errorClass", exception.getClass().getSimpleName())
        .log("token family retention candidate failed");
  }

  /** 자동 정리 실패를 식별자나 예외 메시지 없이 기록한다. */
  public static void sessionCleanupFailure(RuntimeException exception) {
    LOGGER.atWarn().addKeyValue("component", "session-heartbeat-cleanup")
        .addKeyValue("errorClass", exception.getClass().getSimpleName())
        .log("session cleanup candidate failed");
  }

  /** 삭제 계정 정리 실패를 사용자 ID나 예외 메시지 없이 기록한다. */
  public static void accountPurgeFailure(RuntimeException exception) {
    LOGGER.atWarn().addKeyValue("component", "deleted-account-purge")
        .addKeyValue("errorClass", exception.getClass().getSimpleName())
        .log("deleted account purge failed");
  }

  /** 검증에 실패한 READY 학습 정리를 목록에서 뺐음을 세션 ID만으로 기록한다. */
  public static void learningSummaryHidden(long sessionId) {
    LOGGER.atWarn().addKeyValue("component", "learning-summary-list")
        .addKeyValue("event", "summary.invalid.hidden").addKeyValue("sessionId", sessionId)
        .log("invalid learning summary hidden from list");
  }

  /** 요청 시작 시 고정된 필드만 기록한다. */
  public static void httpIn(String method, String path) {
    LOGGER.atInfo().addKeyValue("component", "request-filter").addKeyValue("event", "http.in")
        .addKeyValue("method", safeMethod(method)).addKeyValue("path", safePath(path))
        .addKeyValue("durMs", 0).log("http.in");
  }

  /** 요청 완료 상태와 서버에서 생성한 오류 분류를 기록한다. */
  public static void httpOut(String method, String path, int status, long durationMs,
      Stage stage, ApiErrorCode code) {
    var output = status >= 500 ? LOGGER.atError() : status >= 400
        ? LOGGER.atWarn() : LOGGER.atInfo();
    output.addKeyValue("component", "request-filter").addKeyValue("event", "http.out")
        .addKeyValue("method", safeMethod(method)).addKeyValue("path", safePath(path))
        .addKeyValue("status", status).addKeyValue("durMs", Math.max(0, durationMs));
    if (status >= 400) {
      output.addKeyValue("stage", stage.name().toLowerCase(java.util.Locale.ROOT));
      output.addKeyValue("errorClass", status == 401 || status == 403 ? "auth"
          : status == 429 ? "rate_limit" : "unknown");
      if (code != null) {
        output.addKeyValue("code", code.name());
      } else if (status == 404 || status == 405) {
        // 프레임워크 오류는 도메인 예외 속성 없이 반환되므로 상태에서 고정값만 유도한다.
        output.addKeyValue("code", status == 404 ? "NOT_FOUND" : "METHOD_NOT_ALLOWED");
      }
    }
    output.log("http.out");
  }

  /** 요청 값이 아닌 제한된 필드 이름과 예상 타입만 기록한다. */
  public static void validationFailure(String field, Class<?> type) {
    var event = LOGGER.atWarn().addKeyValue("component", "validation").addKeyValue("durMs", 0)
        .addKeyValue("event", "validate.fail").addKeyValue("status", 400);
    if (field != null && field.matches("[A-Za-z][A-Za-z0-9]{0,63}")) {
      event.addKeyValue("field", field).addKeyValue("expectedType", safeType(type));
    }
    event.log("validate.fail");
  }

  private static String safeType(Class<?> type) {
    if (type == String.class || type == Boolean.class || type == Integer.class
        || type == long.class || type == Long.class || type == Double.class
        || type == BigDecimal.class) {
      return type.getSimpleName();
    }
    return "unknown";
  }

  private static String safeMethod(String method) {
    return SAFE_METHODS.contains(method) ? method : "OTHER";
  }

  private static String safePath(String path) {
    if (path == null || path.length() > 2048) {
      return "/api/v1/{redacted}";
    }
    StringBuilder safe = new StringBuilder();
    for (String segment : path.split("/")) {
      if (!segment.isEmpty()) {
        safe.append('/').append(SAFE_SEGMENTS.contains(segment) ? segment : "{redacted}");
      }
    }
    return safe.isEmpty() ? "/" : safe.toString();
  }

  /** 요청 실패가 확인된 단계다. */
  public enum Stage { SECURITY, VALIDATION, DATABASE, SERVER, REQUEST }
}
