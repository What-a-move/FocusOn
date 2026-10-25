package com.capstone.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.capstone.backend.auth.controller.TokenLifecycleController;
import com.capstone.backend.auth.dto.TokenLifecycleDtos.RefreshRequest;
import com.capstone.backend.common.api.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TokenLifecycleContractTests {
  @Test
  void rotatingUnknownTokensCannotBypassIpLimitOrReachServiceAfterLimit() {
    var service = mock(TokenLifecycleService.class);
    var controller = new TokenLifecycleController(service, new AuthRateLimiter());
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.1");
    for (int i = 0; i < 300; i++) {
      controller.refresh(new RefreshRequest("synthetic-" + i), request);
    }
    var blocked = assertThrows(ApiException.class,
        () -> controller.refresh(new RefreshRequest("another-token"), request));
    assertTrue(blocked.retryAfterSeconds() > 0);
    verify(service, times(300)).refresh(any());
    request.setRemoteAddr("192.0.2.2");
    assertDoesNotThrow(() -> controller.refresh(new RefreshRequest("other-ip"), request));
    verify(service, times(301)).refresh(any());
  }

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
