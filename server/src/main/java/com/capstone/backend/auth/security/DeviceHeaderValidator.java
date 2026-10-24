package com.capstone.backend.auth.security;

import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;

/** 각 API가 명세상 필수인 기기 헤더를 principal과 대조할 때 사용한다. */
public final class DeviceHeaderValidator {
  private DeviceHeaderValidator() {}

  /** 명세상 필수 헤더의 누락과 다른 기기 위장을 구분한다. */
  public static void require(String deviceId, FocusOnPrincipal principal) {
    if (deviceId == null || deviceId.isBlank()) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    if (!Long.toString(principal.deviceId()).equals(deviceId)) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
  }
}
