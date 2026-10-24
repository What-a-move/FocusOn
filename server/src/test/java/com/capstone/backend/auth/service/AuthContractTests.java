package com.capstone.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capstone.backend.common.api.ApiException;
import java.time.Duration;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

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
