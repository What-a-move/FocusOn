package com.capstone.backend.common.api;

import org.springframework.http.HttpStatus;

/** 현재 공통 기반에서 직접 사용하는 Notion 공통 오류 코드다. */
public enum ApiErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요.", false),
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", true),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 다시 시도해 주세요.", false);

  private final HttpStatus status;
  private final String message;
  private final boolean retryable;

  ApiErrorCode(HttpStatus status, String message, boolean retryable) {
    this.status = status;
    this.message = message;
    this.retryable = retryable;
  }

  /** 코드에 대응하는 HTTP 상태를 반환한다. */
  public HttpStatus status() {
    return status;
  }

  /** 내부 예외 원문 대신 사용할 안전한 사용자 안내를 반환한다. */
  public String defaultMessage() {
    return message;
  }

  /** 클라이언트가 재시도를 고려할 수 있는지 반환한다. */
  public boolean retryable() {
    return retryable;
  }
}
