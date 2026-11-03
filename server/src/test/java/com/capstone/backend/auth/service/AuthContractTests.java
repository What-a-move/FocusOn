package com.capstone.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.capstone.backend.auth.controller.AuthController;
import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.common.api.ApiException;
import java.time.Duration;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthContractTests {
  @Test
  void loopbackAllowsOnlyExactHostAndDefaultPath() {
    assertDoesNotThrow(() -> RedirectUriValidator.validate("http://127.0.0.1:49152"));
    assertDoesNotThrow(() -> RedirectUriValidator.validate("http://127.0.0.1:49152/"));
    for (String value : new String[] {"https://127.0.0.1:1234/", "http://localhost:1234/",
        "http://127.0.0.1/", "http://127.0.0.1:65536/", "http://user@127.0.0.1:1234/",
        "http://127.0.0.1:1234/callback", "http://127.0.0.1:1234/?x=1",
        "http://127.0.0.1:1234/#x", "http://127.0.0.1.evil.test:1234/"}) {
      assertThrows(ApiException.class, () -> RedirectUriValidator.validate(value), value);
    }
  }

  @Test
  void limitsAreAtomicPerKeyAndProvidePositiveRetryAfter() {
    AuthRateLimiter limits = new AuthRateLimiter();
    for (int i = 0; i < 5; i++) {
      limits.check("same", 5, Duration.ofMinutes(10));
    }
    ApiException blocked = assertThrows(ApiException.class,
        () -> limits.check("same", 5, Duration.ofMinutes(10)));
    assertTrue(blocked.retryAfterSeconds() > 0);
    assertDoesNotThrow(() -> limits.check("other", 5, Duration.ofMinutes(10)));
  }

  @Test
  void anonymousCapacityDoesNotBlockAuthenticatedApi() {
    AuthRateLimiter limits = new AuthRateLimiter();
    for (int i = 0; i < 10000; i++) {
      limits.check("google:" + i, 5, Duration.ofMinutes(10));
    }
    assertThrows(ApiException.class,
        () -> limits.check("google:overflow", 5, Duration.ofMinutes(10)));
    assertDoesNotThrow(() -> limits.check("api:1:1", 60, Duration.ofMinutes(1)));
  }

  @Test
  void pairingInstallationCapacityIsBoundedPerIp() {
    AuthRateLimiter limits = new AuthRateLimiter();
    for (int i = 0; i < 32; i++) {
      limits.checkPairingInstallation("192.0.2.1", "installation-" + i);
    }
    var blocked = assertThrows(ApiException.class,
        () -> limits.checkPairingInstallation("192.0.2.1", "another"));
    assertTrue(blocked.retryAfterSeconds() > 0);
    assertDoesNotThrow(() -> limits.checkPairingInstallation("192.0.2.1", "installation-0"));
    assertDoesNotThrow(() -> limits.checkPairingInstallation("192.0.2.2", "new-user"));
    assertDoesNotThrow(() -> limits.checkPairingIp("192.0.2.3"));
    assertDoesNotThrow(() -> limits.check("api:1:1", 60, Duration.ofMinutes(1)));
  }

  @Test
  void pairingRetainsTenRequestsPerIpAndInstallation() {
    AuthRateLimiter limits = new AuthRateLimiter();
    for (int i = 0; i < 10; i++) {
      limits.checkPairingInstallation("192.0.2.1", "same-installation");
    }
    assertThrows(ApiException.class,
        () -> limits.checkPairingInstallation("192.0.2.1", "same-installation"));
    assertDoesNotThrow(() -> limits.checkPairingInstallation("192.0.2.1", "another"));
    assertDoesNotThrow(() -> limits.checkPairingInstallation("192.0.2.2", "same-installation"));
  }

  @Test
  void pairingInstallationCapacityRecoversAtWindowExpiry() {
    var time = new AtomicLong(1_000);
    AuthRateLimiter limits = new AuthRateLimiter(time::get);
    for (int i = 0; i < 32; i++) {
      limits.checkPairingInstallation("192.0.2.1", "installation-" + i);
    }
    time.addAndGet(Duration.ofMinutes(10).toMillis() - 1);
    assertEquals(1L, assertThrows(ApiException.class,
        () -> limits.checkPairingInstallation("192.0.2.1", "new")).retryAfterSeconds());
    time.incrementAndGet();
    assertDoesNotThrow(() -> limits.checkPairingInstallation("192.0.2.1", "new"));
  }

  @Test
  void pairingIpGroupCapacityIsBoundedAndCleansExpiredGroups() {
    var time = new AtomicLong(1_000);
    AuthRateLimiter limits = new AuthRateLimiter(time::get);
    for (int i = 0; i < 10000; i++) {
      limits.checkPairingInstallation("synthetic-ip-" + i, "installation");
    }
    assertThrows(ApiException.class,
        () -> limits.checkPairingInstallation("new-ip", "installation"));
    time.addAndGet(Duration.ofMinutes(10).toMillis());
    assertDoesNotThrow(() -> limits.checkPairingInstallation("new-ip", "installation"));
  }

  @Test
  void changingInstallationCannotExhaustLoginCapacityFromOneIp() {
    AuthRateLimiter limits = new AuthRateLimiter();
    for (int i = 0; i < 300; i++) {
      limits.checkGoogleLogin("192.0.2.1", "installation-" + i);
    }
    for (int i = 300; i < 11000; i++) {
      final int installation = i;
      assertThrows(ApiException.class,
          () -> limits.checkGoogleLogin("192.0.2.1", "installation-" + installation));
    }
    assertDoesNotThrow(() -> limits.checkGoogleLogin("192.0.2.2", "new-user"));
    assertDoesNotThrow(() -> limits.check("api:1:1", 60, Duration.ofMinutes(1)));
  }

  @Test
  void loginPreservesFiveAttemptsPerIpAndInstallation() {
    AuthRateLimiter limits = new AuthRateLimiter();
    for (int i = 0; i < 5; i++) {
      limits.checkGoogleLogin("192.0.2.1", "same-installation");
    }
    ApiException blocked = assertThrows(ApiException.class,
        () -> limits.checkGoogleLogin("192.0.2.1", "same-installation"));
    assertTrue(blocked.retryAfterSeconds() > 0);
    assertDoesNotThrow(() -> limits.checkGoogleLogin("192.0.2.1", "other-installation"));
  }

  @Test
  void controllerBlocksRotatingInstallationsBeforeCallingGoogleLogin() {
    LoginService login = mock(LoginService.class);
    AuthController controller = new AuthController(login, new AuthRateLimiter());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.1");
    for (int i = 0; i < 300; i++) {
      controller.exchange(exchangeRequest("installation-" + i), request);
    }
    assertThrows(ApiException.class,
        () -> controller.exchange(exchangeRequest("blocked-installation"), request));
    verify(login, times(300)).exchange(any());
    request.setRemoteAddr("192.0.2.2");
    assertDoesNotThrow(() -> controller.exchange(exchangeRequest("other-ip"), request));
    verify(login, times(301)).exchange(any());
  }

  private GoogleExchangeRequest exchangeRequest(String installationId) {
    return new GoogleExchangeRequest("synthetic-code", "a".repeat(43), "synthetic-state",
        "http://127.0.0.1:49152", new DesktopDevice(installationId, "QA Desktop", "MACOS"));
  }

  @Test
  void opaqueRefreshTokensHaveDistinctHashesAndNoRawPersistence() {
    var values = new HashSet<String>();
    for (int i = 0; i < 100; i++) {
      String token = TokenSecrets.newRefreshToken();
      assertTrue(token.length() >= 43);
      assertEquals(64, TokenSecrets.hash(token).length());
      assertTrue(values.add(token));
    }
  }
}
