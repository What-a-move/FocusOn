package com.capstone.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Access JWT 발급과 검증의 기본 계약을 확인한다. */
class AccessJwtServiceTests {
  private static final String KEY = Base64.getEncoder().encodeToString(
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
  private static final String ISSUER = "focuson";
  private static final String AUDIENCE = "focuson-api";

  @Test
  @DisplayName("인증된 식별자로 발급한 토큰을 검증한다")
  void issuesAndVerifiesToken() {
    AccessJwtService service = new AccessJwtService(KEY, ISSUER, AUDIENCE);
    Instant beforeIssue = Instant.now();

    AccessJwtService.IssuedAccessToken issued = service.issue(11L, 22L, 33L);

    assertEquals(new AccessJwtService.AccessTokenClaims(11L, 22L, 33L),
        service.verify(issued.token()));
    assertTrue(issued.expiresAt().isAfter(beforeIssue.plus(Duration.ofMinutes(14))));
    assertFalse(issued.toString().contains(issued.token()));
  }

  @Test
  @DisplayName("서명이 변경된 토큰을 거절한다")
  void rejectsTamperedSignature() {
    AccessJwtService service = new AccessJwtService(KEY, ISSUER, AUDIENCE);
    String token = service.issue(11L, 22L, 33L).token();
    int signatureStart = token.lastIndexOf('.') + 1;
    char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
    String tampered = token.substring(0, signatureStart) + replacement
        + token.substring(signatureStart + 1);

    assertEquals("Invalid access token",
        assertThrows(IllegalArgumentException.class, () -> service.verify(tampered))
            .getMessage());
  }

  @Test
  @DisplayName("발급자 또는 대상이 다른 토큰을 거절한다")
  void rejectsOtherIssuerOrAudience() {
    AccessJwtService service = new AccessJwtService(KEY, ISSUER, AUDIENCE);

    assertThrows(IllegalArgumentException.class,
        () -> service.verify(signedToken("other-issuer", AUDIENCE)));
    assertThrows(IllegalArgumentException.class,
        () -> service.verify(signedToken(ISSUER, "other-audience")));
  }

  @Test
  @DisplayName("비어 있거나 잘못된 식별자를 거절한다")
  void rejectsMissingTokenAndInvalidIds() {
    AccessJwtService service = new AccessJwtService(KEY, ISSUER, AUDIENCE);

    assertThrows(IllegalArgumentException.class, () -> service.verify(" "));
    assertThrows(IllegalArgumentException.class, () -> service.issue(0L, 22L, 33L));
    assertThrows(IllegalArgumentException.class, () -> service.issue(11L, -1L, 33L));
  }

  @Test
  @DisplayName("유효하지 않거나 짧은 서명 키를 거절한다")
  void rejectsInvalidSigningKeys() {
    assertThrows(IllegalArgumentException.class,
        () -> new AccessJwtService(null, ISSUER, AUDIENCE));
    assertThrows(IllegalArgumentException.class,
        () -> new AccessJwtService("not-base64!", ISSUER, AUDIENCE));
    assertThrows(IllegalArgumentException.class,
        () -> new AccessJwtService(Base64.getEncoder().encodeToString(new byte[31]),
            ISSUER, AUDIENCE));
  }

  @Test
  void distinguishesVerifiedExpiryFromForgedExpiredPayload() {
    AccessJwtService service = new AccessJwtService(KEY, ISSUER, AUDIENCE);
    SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(KEY), "HmacSHA256");
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).audience(List.of(AUDIENCE))
        .subject("11").issuedAt(now.minusSeconds(120)).expiresAt(now.minusSeconds(1))
        .id("expired-jti").claim("tokenType", "ACCESS").claim("deviceId", "22")
        .claim("tokenFamilyId", "33").build();
    String expired = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build()
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
    assertThrows(AccessJwtService.ExpiredAccessTokenException.class, () -> service.verify(expired));
    int start = expired.lastIndexOf('.') + 1;
    char replacement = expired.charAt(start) == 'A' ? 'B' : 'A';
    String forged = expired.substring(0, start) + replacement + expired.substring(start + 1);
    IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
        () -> service.verify(forged));
    assertFalse(rejected instanceof AccessJwtService.ExpiredAccessTokenException);
  }

  private static String signedToken(String issuer, String audience) {
    byte[] keyBytes = Base64.getDecoder().decode(KEY);
    SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .audience(List.of(audience))
        .subject("11")
        .issuedAt(now)
        .expiresAt(now.plus(Duration.ofMinutes(15)))
        .id("test-jti")
        .claim("tokenType", "ACCESS")
        .claim("deviceId", "22")
        .claim("tokenFamilyId", "33")
        .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    return NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build()
        .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
