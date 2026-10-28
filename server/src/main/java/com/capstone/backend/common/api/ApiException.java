package com.capstone.backend.common.api;

import java.util.Map;

/** 명세에 있는 오류 코드와 안전한 details를 HTTP 계층에 전달한다. */
public final class ApiException extends RuntimeException {
  private final ApiErrorCode code;
  private final Map<String, Object> details;
  private final Long retryAfterSeconds;

  /** 확정된 오류 코드로 API 예외를 만든다. */
  public ApiException(ApiErrorCode code) {
    this(code, null, Map.of());
  }

  private ApiException(
      ApiErrorCode code, Long retryAfterSeconds, Map<String, Object> details) {
    super(code.defaultMessage());
    if (code == ApiErrorCode.TOO_MANY_REQUESTS
        && (retryAfterSeconds == null || retryAfterSeconds < 1)) {
      throw new IllegalArgumentException("429 requires a positive Retry-After");
    }
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
    this.details = Map.copyOf(details);
  }

  /** 기기 한도처럼 명세에 정의된 안전한 부가 정보만 담는다. */
  public static ApiException withDetails(ApiErrorCode code, Map<String, Object> details) {
    return new ApiException(code, null, details);
  }

  /** 429 응답의 본문과 Retry-After 헤더에 같은 대기 시간을 사용한다. */
  public static ApiException tooManyRequests(long retryAfterSeconds) {
    if (retryAfterSeconds < 1) {
      throw new IllegalArgumentException("Retry-After must be positive");
    }
    return new ApiException(ApiErrorCode.TOO_MANY_REQUESTS, retryAfterSeconds, Map.of());
  }

  /** 입력 잠금의 410 응답에도 남은 대기 시간과 시도 횟수를 함께 제공한다. */
  public static ApiException pairingLocked(long retryAfterSeconds) {
    if (retryAfterSeconds < 1) {
      throw new IllegalArgumentException("Retry-After must be positive");
    }
    return new ApiException(ApiErrorCode.LINK_004_ATTEMPTS_EXCEEDED, retryAfterSeconds,
        Map.of("remainingAttempts", 0));
  }

  /** 클라이언트가 분기할 오류 코드를 반환한다. */
  public ApiErrorCode code() {
    return code;
  }

  /** 공개 가능한 최소 details를 반환한다. */
  public Map<String, Object> details() {
    return details;
  }

  /** 429의 재시도 대기 시간 또는 null을 반환한다. */
  public Long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
