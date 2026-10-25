package com.capstone.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.capstone.backend.common.api.ApiException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class GoogleOauthClientTests {
  private static KeyPair keys;

  @BeforeAll
  static void createSyntheticSigningKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keys = generator.generateKeyPair();
  }

  @Test
  void acceptsOnlySignedGoogleIdentityWithExpectedAudienceAndSubject() {
    GoogleOauthClient client = client(NimbusJwtDecoder.withPublicKey(
        (RSAPublicKey) keys.getPublic()).build());
    assertEquals("synthetic-sub", client.verifyIdentity(token("accounts.google.com",
        "desktop-client", "synthetic-sub", 300)));
    for (String value : new String[] {
        token("https://evil.test", "desktop-client", "synthetic-sub", 300),
        token("accounts.google.com", "other-client", "synthetic-sub", 300),
        token("accounts.google.com", "desktop-client", null, 300),
        token("accounts.google.com", "desktop-client", "synthetic-sub", -1)}) {
      assertEquals("AUTH_OAUTH_REAUTH_REQUIRED",
          assertThrows(ApiException.class, () -> client.verifyIdentity(value)).code().name());
    }
  }

  @Test
  void acceptsMatchingAuthorizedPartyForSingleAndMultipleAudiences() {
    var client = client(NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    assertEquals("synthetic-sub", client.verifyIdentity(token("accounts.google.com",
        List.of("desktop-client"), "synthetic-sub", 300, "desktop-client")));
    assertEquals("synthetic-sub", client.verifyIdentity(token("accounts.google.com",
        List.of("desktop-client", "other-client"), "synthetic-sub", 300, "desktop-client")));
  }

  @Test
  void rejectsAnotherAuthorizedPartyEvenWhenAudienceContainsDesktop() {
    var client = client(NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    for (var audiences : List.of(List.of("desktop-client"),
        List.of("desktop-client", "other-client"))) {
      String value = token("accounts.google.com", audiences, "synthetic-sub", 300, "other-client");
      assertEquals("AUTH_OAUTH_REAUTH_REQUIRED",
          assertThrows(ApiException.class, () -> client.verifyIdentity(value)).code().name());
    }
  }

  @Test
  void rejectsMultipleAudiencesWithoutAuthorizedParty() {
    var client = client(NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    String value = token("accounts.google.com", List.of("desktop-client", "other-client"),
        "synthetic-sub", 300, null);
    assertEquals("AUTH_OAUTH_REAUTH_REQUIRED",
        assertThrows(ApiException.class, () -> client.verifyIdentity(value)).code().name());
  }

  @Test
  void rejectsBlankOrNonStringAuthorizedParty() {
    var client = client(NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    for (Object authorizedParty : List.of("", " ", 123, List.of("desktop-client"))) {
      String value = token("accounts.google.com", List.of("desktop-client"),
          "synthetic-sub", 300, authorizedParty);
      assertEquals("AUTH_OAUTH_REAUTH_REQUIRED",
          assertThrows(ApiException.class, () -> client.verifyIdentity(value)).code().name());
    }
  }

  @Test
  void matchingAuthorizedPartyDoesNotReplaceAudienceValidation() {
    var client = client(NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    String value = token("accounts.google.com", List.of("other-client"),
        "synthetic-sub", 300, "desktop-client");
    assertEquals("AUTH_OAUTH_REAUTH_REQUIRED",
        assertThrows(ApiException.class, () -> client.verifyIdentity(value)).code().name());
  }

  @Test
  void missingAudienceIsAuthenticationFailureWithOrWithoutAuthorizedParty() {
    var client = client(NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    String withoutParty = token("accounts.google.com", null, "synthetic-sub", 300, null);
    String withParty = token("accounts.google.com", null, "synthetic-sub", 300, "desktop-client");
    for (String value : List.of(withoutParty, withParty)) {
      assertEquals("AUTH_OAUTH_REAUTH_REQUIRED",
          assertThrows(ApiException.class, () -> client.verifyIdentity(value)).code().name());
    }
  }

  @Test
  void exchangePostsCodePkceAndUnchangedRedirectToGoogle() throws Exception {
    HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    java.net.http.HttpResponse<String> response = mock(java.net.http.HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"id_token\":\"" + token("accounts.google.com",
        "desktop-client", "synthetic-sub", 300) + "\"}");
    when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
        org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any()))
        .thenReturn(response);
    var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
    var client = new GoogleOauthClient("desktop-client", "", http, decoder);
    var request = new com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest(
        "synthetic-code", "a".repeat(43), "synthetic-state", "http://127.0.0.1:49152",
        new com.capstone.backend.auth.dto.AuthDtos.DesktopDevice("install", "QA", "MACOS"));
    assertEquals("synthetic-sub", client.exchange(request));
    var captured = org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
    org.mockito.Mockito.verify(http).send(captured.capture(),
        org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any());
    assertEquals("https://oauth2.googleapis.com/token", captured.getValue().uri().toString());
    assertEquals("POST", captured.getValue().method());
    assertEquals("application/x-www-form-urlencoded",
        captured.getValue().headers().firstValue("Content-Type").orElseThrow());
    var bytes = new java.io.ByteArrayOutputStream();
    captured.getValue().bodyPublisher().orElseThrow().subscribe(
        new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
          public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          public void onNext(java.nio.ByteBuffer buffer) {
            byte[] part = new byte[buffer.remaining()];
            buffer.get(part);
            bytes.writeBytes(part);
          }

          public void onError(Throwable failure) {
            throw new AssertionError("body publisher failed");
          }

          public void onComplete() {}
        });
    String body = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("code=synthetic-code"));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("code_verifier=" + "a".repeat(43)));
    org.junit.jupiter.api.Assertions.assertTrue(
        body.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A49152"));
  }

  @Test
  void keyServerFailureIsNotReportedAsUserAuthenticationFailure() {
    NimbusJwtDecoder decoder = mock(NimbusJwtDecoder.class);
    GoogleOauthClient client = client(decoder);
    when(decoder.decode("synthetic")).thenThrow(new JwtException("unavailable",
        new com.nimbusds.jose.RemoteKeySourceException(
            "key server unavailable", new java.io.IOException())));
    assertEquals("SERVICE_UNAVAILABLE",
        assertThrows(ApiException.class, () -> client.verifyIdentity("synthetic")).code().name());
    org.mockito.Mockito.doThrow(new JwtException("timeout", new java.net.SocketTimeoutException()))
        .when(decoder).decode("synthetic");
    assertEquals("REQUEST_TIMEOUT",
        assertThrows(ApiException.class, () -> client.verifyIdentity("synthetic")).code().name());
  }

  private GoogleOauthClient client(NimbusJwtDecoder decoder) {
    return new GoogleOauthClient("desktop-client", "", mock(HttpClient.class), decoder);
  }

  private String token(String issuer, String audience, String subject, long expiresIn) {
    return token(issuer, List.of(audience), subject, expiresIn, null);
  }

  private String token(String issuer, List<String> audiences, String subject, long expiresIn,
      Object authorizedParty) {
    final RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
        .privateKey(keys.getPrivate()).keyID("synthetic-key").build();
    var claims = JwtClaimsSet.builder().claim("iss", issuer)
        .issuedAt(Instant.now().minusSeconds(30)).expiresAt(Instant.now().plusSeconds(expiresIn));
    if (audiences != null) {
      claims.audience(audiences);
    }
    if (authorizedParty != null) {
      claims.claim("azp", authorizedParty);
    }
    if (subject != null) {
      claims.subject(subject);
    }
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)))
        .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256)
            .keyId("synthetic-key").build(), claims.build())).getTokenValue();
  }
}
