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

/** Issues and verifies short-lived FocusOn access tokens. */
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

  /** Creates the service with the server signing key and fixed JWT contract. */
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

  /** Issues a 15-minute access token after the caller has authenticated the user and device. */
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

  /** Verifies the signature and mandatory access token claims. */
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

  private static long parseId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id > 0) {
        return id;
      }
    } catch (NumberFormatException exception) {
      // Invalid IDs use the same public error as malformed or expired tokens.
    }
    throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
  }

  private static void requirePositiveId(long id) {
    if (id <= 0) {
      throw new IllegalArgumentException("JWT IDs must be positive");
    }
  }

  /** Verified identity claims carried by an access token. */
  public record AccessTokenClaims(long userId, long deviceId, long tokenFamilyId) {}

  /** An issued token and its expiry, with a redacted string representation. */
  public record IssuedAccessToken(String token, Instant expiresAt) {
    @Override
    public String toString() {
      return "IssuedAccessToken[token=<redacted>, expiresAt=" + expiresAt + "]";
    }
  }
}
