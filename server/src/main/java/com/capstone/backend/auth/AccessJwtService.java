package com.capstone.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** FocusOn의 단기 Access Token을 발급하고 검증한다. */
@Service
public class AccessJwtService {
  private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
  private static final String ACCESS_TOKEN_TYPE = "ACCESS";
  private static final String INVALID_TOKEN_MESSAGE = "Invalid access token";

  private final String audience;
  private final Clock clock;
  private final JwtEncoder encoder;
  private final String issuer;
  private final NimbusJwtDecoder decoder;

  /** 서버 서명 키와 고정된 JWT 계약으로 서비스를 생성한다. */
  public AccessJwtService(
      @Value("${focuson.jwt.secret-base64}") String secretBase64,
      @Value("${focuson.jwt.issuer}") String issuer,
      @Value("${focuson.jwt.audience}") String audience) {
    if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("JWT issuer and audience must be configured");
    }

    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(secretBase64);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("JWT signing key must be valid Base64");
    }
    if (keyBytes.length < 32) {
      throw new IllegalArgumentException("JWT signing key must contain at least 32 bytes");
    }

    final SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    this.issuer = issuer;
    this.audience = audience;
    this.clock = Clock.systemUTC();
    this.encoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build();
    this.decoder = NimbusJwtDecoder.withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();

    JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
    timestampValidator.setAllowEmptyExpiryClaim(false);
    this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        timestampValidator,
        new JwtIssuerValidator(issuer),
        new JwtAudienceValidator(audience)));
  }

  /** 호출자가 사용자와 기기를 인증한 뒤 15분짜리 Access Token을 발급한다. */
  public IssuedAccessToken issue(long userId, long deviceId, long tokenFamilyId) {
    requirePositiveId(userId);
    requirePositiveId(deviceId);
    requirePositiveId(tokenFamilyId);

    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_LIFETIME);
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .audience(List.of(audience))
        .subject(Long.toString(userId))
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .id(UUID.randomUUID().toString())
        .claim("tokenType", ACCESS_TOKEN_TYPE)
        .claim("deviceId", Long.toString(deviceId))
        .claim("tokenFamilyId", Long.toString(tokenFamilyId))
        .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedAccessToken(token, expiresAt);
  }

  /** 서명과 Access Token의 필수 클레임을 검증한다. */
  public AccessTokenClaims verify(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
    }

    try {
      Jwt jwt = decoder.decode(token);
      Instant issuedAt = jwt.getIssuedAt();
      Instant expiresAt = jwt.getExpiresAt();
      if (!ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString("tokenType"))
          || issuedAt == null
          || issuedAt.isAfter(clock.instant())
          || expiresAt == null
          || !issuedAt.isBefore(expiresAt)
          || expiresAt.isAfter(issuedAt.plus(ACCESS_TOKEN_LIFETIME))
          || jwt.getId() == null
          || jwt.getId().isBlank()) {
        throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
      }

      return new AccessTokenClaims(
          parseId(jwt.getSubject()),
          parseId(jwt.getClaimAsString("deviceId")),
          parseId(jwt.getClaimAsString("tokenFamilyId")));
    } catch (JwtException | ClassCastException exception) {
      throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
    }
  }

  /** JWT의 문자열 식별자를 양수인 서버 식별자로 변환한다. */
  private static long parseId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id > 0) {
        return id;
      }
    } catch (NumberFormatException exception) {
      // 잘못된 식별자는 형식 오류나 만료된 토큰과 같은 공개 오류로 처리한다.
    }
    throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
  }

  /** 발급할 토큰에 포함되는 식별자가 양수인지 확인한다. */
  private static void requirePositiveId(long id) {
    if (id <= 0) {
      throw new IllegalArgumentException("JWT IDs must be positive");
    }
  }

  /** 검증된 Access Token의 식별 클레임이다. */
  public record AccessTokenClaims(long userId, long deviceId, long tokenFamilyId) {}

  /** 발급된 토큰과 만료 시각이다. 문자열 표현에서는 토큰을 가린다. */
  public record IssuedAccessToken(String token, Instant expiresAt) {
    /** 토큰 값을 노출하지 않는 문자열을 반환한다. */
    @Override
    public String toString() {
      return "IssuedAccessToken[token=<redacted>, expiresAt=" + expiresAt + "]";
    }
  }
}
