package com.capstone.backend.common.api;

import org.springframework.http.HttpStatus;

/** 현재 공통 기반에서 직접 사용하는 Notion 공통 오류 코드다. */
public enum ApiErrorCode {
  AUTH_ACCESS_TOKEN_EXPIRED(org.springframework.http.HttpStatus.UNAUTHORIZED,
      "인증 시간이 만료됐습니다. 갱신해 주세요.", false),
  AUTH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "인증 정보가 재사용되어 로그아웃됐습니다.", false),
  AUTH_RELOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요.", false),
  AUTH_OAUTH_REAUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "Google 인증을 다시 진행해 주세요.", false),
  AUTH_DEVICE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "기존 Desktop 기기를 해제해 주세요.", false),
  DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "기기를 찾을 수 없습니다.", false),
  LINK_001_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "영문·숫자 6자리 코드를 확인해 주세요.", false),
  LINK_002_EXPIRED(HttpStatus.GONE, "코드가 만료됐습니다. 새 코드를 발급해 주세요.", false),
  LINK_003_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용한 코드입니다.", false),
  LINK_004_ATTEMPTS_EXCEEDED(HttpStatus.GONE, "입력이 잠겼습니다. 잠시 후 다시 입력해 주세요.", false),
  LINK_005_CANCELLED(HttpStatus.GONE, "취소된 코드입니다. 새 코드를 발급해 주세요.", false),
  LINK_006_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "코드가 맞지 않아요. 다시 확인해 주세요.", false),
  LINK_007_INSTALLATION_ALREADY_LINKED(HttpStatus.CONFLICT, "기존 Extension 연결을 먼저 해제해 주세요.", false),
  LINK_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Extension을 다시 연결해 주세요.", false),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 시도해 주세요.", true),
  REQUEST_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "요청 시간이 초과됐습니다.", true),
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요.", false),
  PROFILE_NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "닉네임을 확인해 주세요.", false),
  PROFILE_NICKNAME_DUPLICATE(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.", false),
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
