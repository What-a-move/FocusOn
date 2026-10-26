package com.capstone.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capstone.backend.common.api.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenLifecycleContractTests {
  /** 공개 한도 자체는 301번째 요청을 차단하고 다른 IP의 첫 요청은 허용한다. */
  @Test
  void refreshIpLimitBlocksAt301AndPreservesOtherIp() {
    var limits = new AuthRateLimiter();
    for (int i = 0; i < 300; i++) {
      limits.checkRefreshIp("192.0.2.1");
    }
    var blocked = assertThrows(ApiException.class,
        () -> limits.checkRefreshIp("192.0.2.1"));
    assertTrue(blocked.retryAfterSeconds() > 0);
    assertDoesNotThrow(() -> limits.checkRefreshIp("192.0.2.2"));
  }

  /** 공개 refresh 공간 포화가 로그인·인증 API 요청을 차단하지 않는지 확인한다. */
  @Test
  void refreshCapacityIsSeparateFromGoogleAndAuthenticatedApiCapacity() {
    var limiter = new AuthRateLimiter();
    for (int i = 0; i < 10000; i++) {
      limiter.check("refresh:" + i, 300, Duration.ofMinutes(10));
    }
    assertThrows(ApiException.class,
        () -> limiter.checkRefreshIp("192.0.2.1"));
    assertDoesNotThrow(() -> limiter.checkGoogleLogin("192.0.2.1", "synthetic-installation"));
    assertDoesNotThrow(() -> limiter.check("api:1:1", 60, Duration.ofMinutes(1)));
  }
}
