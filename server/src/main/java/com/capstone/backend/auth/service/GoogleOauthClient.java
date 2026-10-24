package com.capstone.backend.auth.service;

import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 고정 Google endpoint에 code·PKCE를 교환하고 공개 키로 ID token을 검증한다. */
@Service
public class GoogleOauthClient {
  private static final URI TOKEN_ENDPOINT = URI.create("https://oauth2.googleapis.com/token");
  private final String clientId;
  private final String clientSecret;
  private final HttpClient http;
  private final NimbusJwtDecoder decoder;
  private final JsonMapper json = JsonMapper.builder().build();

  /** Desktop과 같은 Client ID를 서버 설정으로 받는다. 비밀값은 객체 문자열에 노출하지 않는다. */
  @org.springframework.beans.factory.annotation.Autowired
  public GoogleOauthClient(
      @Value("${focuson.google.client-id:}") String clientId,
      @Value("${focuson.google.client-secret:}") String clientSecret) {
    this(clientId, clientSecret,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build(), googleDecoder());
  }

  GoogleOauthClient(String clientId, String clientSecret, HttpClient http,
      NimbusJwtDecoder decoder) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.http = http;
    this.decoder = decoder;
    OAuth2TokenValidator<Jwt> issuer = jwt -> {
      String value = jwt.getClaimAsString("iss");
      return "accounts.google.com".equals(value) || "https://accounts.google.com".equals(value)
          ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    };
    this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(Duration.ZERO), issuer,
        new JwtAudienceValidator(clientId.isBlank() ? "unconfigured" : clientId)));
  }

  /** 일회성 code는 자동 재전송하지 않는다. Google 원문·토큰은 로그에 기록하지 않는다. */
  public String exchange(GoogleExchangeRequest request) {
    if (clientId.isBlank()) {
      throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
    }
    Map<String, String> fields = new java.util.LinkedHashMap<>();
    fields.put("grant_type", "authorization_code");
    fields.put("client_id", clientId);
    fields.put("code", request.authorizationCode());
    fields.put("code_verifier", request.codeVerifier());
    fields.put("redirect_uri", request.redirectUri());
    if (!clientSecret.isBlank()) {
      fields.put("client_secret", clientSecret);
    }
    String form = fields.entrySet().stream().map(entry -> encode(entry.getKey())
        + "=" + encode(entry.getValue())).collect(java.util.stream.Collectors.joining("&"));
    HttpRequest outgoing = HttpRequest.newBuilder(TOKEN_ENDPOINT).timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form)).build();
    try {
      HttpResponse<String> response = http.send(outgoing, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 429 || response.statusCode() >= 500) {
        throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
      }
      if (response.statusCode() != 200) {
        throw new ApiException(ApiErrorCode.AUTH_OAUTH_REAUTH_REQUIRED);
      }
      JsonNode payload = json.readTree(response.body());
      if (payload == null || !payload.isObject()) {
        throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
      }
      String idToken = payload.path("id_token").asText("");
      if (idToken.isBlank()) {
        throw new ApiException(ApiErrorCode.AUTH_OAUTH_REAUTH_REQUIRED);
      }
      return verifyIdentity(idToken);
    } catch (HttpTimeoutException exception) {
      throw new ApiException(ApiErrorCode.REQUEST_TIMEOUT);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
    } catch (IOException exception) {
      throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
    } catch (tools.jackson.core.JacksonException exception) {
      throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
    }
  }

  String verifyIdentity(String token) {
    try {
      Jwt jwt = decoder.decode(token);
      String subject = jwt.getSubject();
      if (subject == null || subject.isBlank() || subject.length() > 255
          || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(Instant.now())
          || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(Instant.now())) {
        throw new ApiException(ApiErrorCode.AUTH_OAUTH_REAUTH_REQUIRED);
      }
      return subject;
    } catch (JwtException exception) {
      // 키 서버 장애는 사용자의 인증 거부와 다르다. 원문·토큰은 공개하지 않는다.
      Throwable cause = exception;
      boolean unavailable = false;
      for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
        if (cause instanceof java.net.SocketTimeoutException
            || cause instanceof HttpTimeoutException) {
          throw new ApiException(ApiErrorCode.REQUEST_TIMEOUT);
        }
        if (cause instanceof com.nimbusds.jose.RemoteKeySourceException
            || cause instanceof org.springframework.web.client.ResourceAccessException) {
          unavailable = true;
        }
      }
      if (unavailable) {
        throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
      }
      throw new ApiException(ApiErrorCode.AUTH_OAUTH_REAUTH_REQUIRED);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(ApiErrorCode.AUTH_OAUTH_REAUTH_REQUIRED);
    }
  }

  private static NimbusJwtDecoder googleDecoder() {
    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(5));
    return NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
        .restOperations(new org.springframework.web.client.RestTemplate(factory)).build();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
