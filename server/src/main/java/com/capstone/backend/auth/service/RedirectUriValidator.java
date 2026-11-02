package com.capstone.backend.auth.service;

import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import java.net.URI;
import java.net.URISyntaxException;

/** Native Desktop의 loopback 기본 주소만 허용한다. 검증 후 원문을 그대로 교환한다. */
public final class RedirectUriValidator {
  private RedirectUriValidator() {}

  /** 임의 host·경로·userinfo·query로 인증 코드를 전송하지 않게 제한한다. */
  public static void validate(String value) {
    try {
      URI uri = new URI(value);
      String path = uri.getRawPath();
      if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
          || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getRawUserInfo() != null
          || uri.getRawQuery() != null || uri.getRawFragment() != null
          || !(path.isEmpty() || "/".equals(path))) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
    } catch (URISyntaxException | NullPointerException exception) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
  }
}
