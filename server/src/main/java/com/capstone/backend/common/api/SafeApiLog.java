package com.capstone.backend.common.api;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 허용한 이벤트와 메타데이터만 로그로 내보내는 API 로거다. */
public final class SafeApiLog {
  private static final Logger LOGGER = LoggerFactory.getLogger(SafeApiLog.class);
  private static final Set<String> SAFE_SEGMENTS = Set.of(
      "api", "v1", "auth", "google", "exchange", "refresh", "logout", "me", "users",
      "profile", "consents", "focus-settings", "analysis-excluded-domains",
      "onboarding-readiness", "pairing-codes", "extension-pairings", "devices", "goals",
      "clarify", "sessions", "active", "pause", "resume", "extend", "end", "heartbeats",
      "local-events", "analysis-runs", "interventions", "acknowledgements", "summary",
      "learning-summary", "reports", "calendar", "daily", "weekly");
  private static final Set<String> SAFE_METHODS = Set.of(
      "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

  private SafeApiLog() {}

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
        || type == Long.class || type == Double.class) {
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
