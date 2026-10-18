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

/**
 * FocusOn의 15분 Access JWT를 발급하고 서명·클레임을 검증한다.
 *
 * <p>이 서비스는 사용자 인증 또는 기기·토큰 패밀리의 활성 상태를 조회하지 않는다.
 * 로그인 흐름은 인증된 식별자로 {@link #issue(long, long, long)}를 호출하고,
 * 요청 인증 흐름은 {@link #verify(String)} 이후 저장소에서 활성 상태를 확인해야 한다.
 */
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

  /** 설정된 서명 키와 issuer·audience로 발급기와 검증기를 생성한다. */
  public AccessJwtService(
      @Value("${focuson.jwt.secret-base64}") String secretBase64,
      @Value("${focuson.jwt.issuer}") String issuer,
      @Value("${focuson.jwt.audience}") String audience) {
    if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("JWT issuer and audience must be configured");
    }

    if (secretBase64 == null || secretBase64.isBlank()) {
      throw new IllegalArgumentException("JWT signing key must be valid Base64");
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

    // Access Token은 짧게 사용하므로 만료 시각의 허용 오차를 두지 않는다.
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
    timestampValidator.setAllowEmptyExpiryClaim(false);
    this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        timestampValidator,
        new JwtIssuerValidator(issuer),
        new JwtAudienceValidator(audience)));
  }

  /**
   * 로그인 또는 갱신 흐름에서 인증·활성 상태 확인을 마친 식별자로 토큰을 발급한다.
   *
   * <p>이 메서드는 식별자의 양수 여부만 확인하며 DB 상태를 검증하지 않는다.
   */
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

  /**
   * 서명, 발급자, 대상, 시간과 필수 식별 클레임을 검증한다.
   *
   * <p>반환된 식별자를 권한 부여에 사용하기 전에 사용자·기기·토큰 패밀리의
   * 현재 활성 상태를 저장소에서 확인해야 한다. 실패 시 토큰 내용을 노출하지 않는
   * {@link IllegalArgumentException}을 던진다.
   */
  public AccessTokenClaims verify(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
    }

    try {
      Jwt jwt = decoder.decode(token);
      Instant issuedAt = jwt.getIssuedAt();
      Instant expiresAt = jwt.getExpiresAt();
      // 서명 검증만으로는 다른 종류의 토큰이나 비정상적인 유효 기간을 걸러낼 수 없다.
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

  private static void requirePositiveId(long id) {
    if (id <= 0) {
      throw new IllegalArgumentException("JWT IDs must be positive");
    }
  }

  /** 서명과 필수 클레임 검증을 통과한 식별자다. 활성 상태 검사는 별도로 수행한다. */
  public record AccessTokenClaims(long userId, long deviceId, long tokenFamilyId) {}

  /** 발급된 토큰과 만료 시각이다. 로그에 객체가 찍혀도 원문 토큰을 노출하지 않는다. */
  public record IssuedAccessToken(String token, Instant expiresAt) {
    @Override
    public String toString() {
      return "IssuedAccessToken[token=<redacted>, expiresAt=" + expiresAt + "]";
    }
  }
}
