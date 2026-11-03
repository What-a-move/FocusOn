package com.capstone.backend.common.api;

import java.util.Map;

/** Controller 오류 응답이 사용하는 최상위 필드다. */
public record ApiErrorResponse(
    String code,
    String message,
    boolean retryable,
    Long retryAfterSeconds,
    String requestId,
    Map<String, Object> details) {

  /** 호출자가 전달한 details를 이후에 바꾸지 못하도록 복사한다. */
  public ApiErrorResponse {
    if ("TOO_MANY_REQUESTS".equals(code)
        && (retryAfterSeconds == null || retryAfterSeconds < 1)) {
      throw new IllegalArgumentException("429 requires a positive Retry-After");
    }
    details = Map.copyOf(details);
  }

  /** 확정된 오류 코드와 요청 추적 ID로 공개 오류 응답을 만든다. */
  public static ApiErrorResponse of(
      ApiErrorCode code, Long retryAfterSeconds, String requestId, Map<String, Object> details) {
    return new ApiErrorResponse(code.name(), code.defaultMessage(), code.retryable(),
        retryAfterSeconds, requestId, details);
  }
}
