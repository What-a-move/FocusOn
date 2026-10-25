package com.capstone.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.TokenLifecycleDtos.RefreshRequest;
import com.capstone.backend.auth.dto.TokenLifecycleDtos.TokenPair;
import com.capstone.backend.auth.persistence.RefreshTokenRecordEntity;
import com.capstone.backend.auth.persistence.RefreshTokenRecordRepository;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.auth.service.TokenLifecycleService;
import com.capstone.backend.auth.service.TokenSecrets;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.api.RequestIdFilter;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/** HTTP 응답 뒤 새 transaction에서 폐기를 확인하며 테스트 전체를 rollback으로 감싸지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
class TokenLifecycleApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private TokenLifecycleService lifecycle;
  @Autowired private AccessJwtService jwt;
  @Autowired private UserRepository users;
  @Autowired private UserDeviceRepository devices;
  @Autowired private TokenFamilyRepository families;
  @Autowired private RefreshTokenRecordRepository tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactions;
  @MockitoBean private GoogleOauthClient google;
  @org.springframework.beans.factory.annotation.Value("${focuson.jwt.secret-base64}")
  private String signingKey;
  private MockMvc mvc;
  private TransactionTemplate tx;
  private String subject;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    tx = new TransactionTemplate(transactions);
    subject = "lifecycle-" + UUID.randomUUID();
    when(google.exchange(any())).thenAnswer(call ->
        ((GoogleExchangeRequest) call.getArgument(0)).state());
  }

  @Test
  void refreshRotatesWithoutBearerAndSlidesTokenAndFamilyThirtyDays() throws Exception {
    final Session session = desktop();
    Instant earlierExpiry = Instant.now().plusSeconds(600);
    jdbc.update("update token_families set expires_at=? where id=?",
        Timestamp.from(earlierExpiry), session.familyId());
    jdbc.update("update refresh_token_records set expires_at=? where token_hash=?",
        Timestamp.from(earlierExpiry), TokenSecrets.hash(session.refresh()));
    Instant before = Instant.now();
    String response = mvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON).content(body(session.refresh()))
            .header("Authorization", "Bearer ignored-expired-or-invalid"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.data.accessTokenExpiresAt").isString())
        .andReturn().getResponse().getContentAsString();
    var data = JsonMapper.builder().build().readTree(response).path("data");
    String next = data.path("refreshToken").asString();
    assertNotEquals(session.refresh(), next);
    tx.executeWithoutResult(status -> {
      var old = tokens.findByTokenHash(TokenSecrets.hash(session.refresh())).orElseThrow();
      var current = tokens.findByTokenHash(TokenSecrets.hash(next)).orElseThrow();
      assertEquals(RefreshTokenRecordEntity.Status.ROTATED, old.getStatus());
      assertTrue(old.getUsedAt() != null);
      assertEquals(old.getId(), current.getParentToken().getId());
      assertEquals(old.getTokenFamily().getId(), current.getTokenFamily().getId());
      assertEquals(current.getExpiresAt(), current.getTokenFamily().getExpiresAt());
      assertTrue(current.getExpiresAt().isAfter(before.plus(Duration.ofDays(30)).minusSeconds(1)));
      assertTrue(current.getExpiresAt().isBefore(Instant.now().plus(Duration.ofDays(30))));
    });
    me(data.path("accessToken").asString(), 200);
    assertEquals(1, activeTokenCount(session.familyId()));
  }

  @Test
  void expiredAccessCanRecoverThroughRefreshThenMe() throws Exception {
    final Session session = desktop();
    var key = new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode(signingKey),
        "HmacSHA256");
    var encoder = org.springframework.security.oauth2.jwt.NimbusJwtEncoder.withSecretKey(key)
        .algorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
    var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
        .issuer("focuson").audience(List.of("focuson-api"))
        .subject(Long.toString(session.userId())).id(UUID.randomUUID().toString())
        .issuedAt(Instant.now().minusSeconds(960)).expiresAt(Instant.now().minusSeconds(60))
        .claim("tokenType", "ACCESS").claim("deviceId", Long.toString(session.deviceId()))
        .claim("tokenFamilyId", Long.toString(session.familyId())).build();
    String expired = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters
        .from(org.springframework.security.oauth2.jwt.JwsHeader.with(
            org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_ACCESS_TOKEN_EXPIRED"));
    TokenPair restored = lifecycle.refresh(session.refresh());
    me(restored.accessToken(), 200);
  }

  @Test
  void reusedTokenRevokesFamilyAndCommitsBeforeReturning401() throws Exception {
    final Session first = desktop();
    final Session other = desktop();
    final TokenPair next = lifecycle.refresh(first.refresh());
    refreshError(first.refresh(), "AUTH_TOKEN_REUSE_DETECTED");
    assertEquals(TokenFamilyEntity.Status.REVOKED,
        families.findById(first.familyId()).orElseThrow().getStatus());
    assertEquals(TokenFamilyEntity.RevokedReason.TOKEN_REUSE,
        families.findById(first.familyId()).orElseThrow().getRevokedReason());
    assertEquals(0, activeTokenCount(first.familyId()));
    me(next.accessToken(), 401);
    me(other.access(), 200);
    assertEquals(1, activeTokenCount(other.familyId()));
  }

  @Test
  void missingBlankUnknownAndOversizedRefreshHaveContractErrors() throws Exception {
    mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_RELOGIN_REQUIRED"));
    for (String value : List.of("{}", "{\"refreshToken\":null}", body(""), body(" "),
        body("unknown-token"))) {
      mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
              .content(value)).andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTH_RELOGIN_REQUIRED"));
    }
    mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(body("x".repeat(4097)))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void expiredRecordAndExpiredFamilyCannotBeRevived() throws Exception {
    final Session expiredToken = desktop();
    Instant now = Instant.now();
    jdbc.update("update refresh_token_records set issued_at=?,expires_at=? where token_hash=?",
        Timestamp.from(now.minusSeconds(600)), Timestamp.from(now.minusSeconds(1)),
        TokenSecrets.hash(expiredToken.refresh()));
    refreshError(expiredToken.refresh(), "AUTH_RELOGIN_REQUIRED");
    final Session expiredFamily = desktop();
    expireFamily(expiredFamily.familyId());
    refreshError(expiredFamily.refresh(), "AUTH_RELOGIN_REQUIRED");
    assertEquals(1, tokenCount(expiredFamily.familyId()));
  }

  @Test
  void deletedUserAndRevokedDeviceCannotRefresh() throws Exception {
    final Session deleted = desktop();
    jdbc.update("update users set deleted_at=? where id=?", Timestamp.from(Instant.now()),
        deleted.userId());
    refreshError(deleted.refresh(), "AUTH_RELOGIN_REQUIRED");
    subject = "another-" + UUID.randomUUID();
    final Session revoked = desktop();
    jdbc.update("update user_devices set status='REVOKED' where id=?", revoked.deviceId());
    refreshError(revoked.refresh(), "AUTH_RELOGIN_REQUIRED");
  }

  @Test
  void concurrentSameTokenRotatesOnceThenRevokesOnReuse() throws Exception {
    final Session session = desktop();
    List<Object> results = race(() -> lifecycle.refresh(session.refresh()),
        () -> lifecycle.refresh(session.refresh()));
    assertEquals(1, results.stream().filter(TokenPair.class::isInstance).count());
    assertEquals(1, results.stream().filter(value -> value instanceof ApiException error
        && error.code().name().equals("AUTH_TOKEN_REUSE_DETECTED")).count());
    assertEquals(TokenFamilyEntity.Status.REVOKED,
        families.findById(session.familyId()).orElseThrow().getStatus());
    assertEquals(0, activeTokenCount(session.familyId()));
    TokenPair issued = (TokenPair) results.stream().filter(TokenPair.class::isInstance)
        .findFirst().orElseThrow();
    me(issued.accessToken(), 401);
  }

  @Test
  void logoutRevokesOnlyCurrentDesktopAndLinkedExtensions() throws Exception {
    final Session first = desktop();
    final Session second = desktop();
    final Session child = extension(first);
    final Session otherChild = extension(second);
    mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + first.access()))
        .andExpect(status().isNoContent()).andExpect(content().string(""));
    me(first.access(), 401);
    me(child.access(), 401);
    me(second.access(), 200);
    me(otherChild.access(), 200);
    assertEquals(UserDeviceEntity.Status.LOGGED_OUT,
        devices.findById(first.deviceId()).orElseThrow().getStatus());
    assertEquals(UserDeviceEntity.Status.REVOKED,
        devices.findById(child.deviceId()).orElseThrow().getStatus());
    assertEquals(0, activeTokenCount(first.familyId()));
    assertEquals(0, activeTokenCount(child.familyId()));
    refreshError(first.refresh(), "AUTH_TOKEN_REUSE_DETECTED");
    // 폐기 사유를 재사용 탐지로 덮지 않는다.
    assertEquals(TokenFamilyEntity.RevokedReason.LOGOUT,
        families.findById(first.familyId()).orElseThrow().getRevokedReason());
  }

  @Test
  void extensionCanRefreshButCannotLogoutDesktop() throws Exception {
    final Session desktop = desktop();
    final Session child = extension(desktop);
    final TokenPair next = lifecycle.refresh(child.refresh());
    me(next.accessToken(), 200);
    mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + next.accessToken()))
        .andExpect(status().isUnauthorized());
    me(desktop.access(), 200);
    me(next.accessToken(), 200);
  }

  @Test
  void extensionCannotRefreshWithoutLiveOwnedDesktopFamily() throws Exception {
    final Session desktop = desktop();
    final Session child = extension(desktop);
    expireFamily(desktop.familyId());
    refreshError(child.refresh(), "AUTH_RELOGIN_REQUIRED");
    me(child.access(), 401);
    assertEquals(1, tokenCount(child.familyId()));
  }

  @Test
  void extensionWithMissingParentIsRejected() throws Exception {
    final Session child = extension(desktop());
    jdbc.update("update user_devices set linked_desktop_device_id=null where id=?",
        child.deviceId());
    refreshError(child.refresh(), "AUTH_RELOGIN_REQUIRED");
    me(child.access(), 401);
  }

  @Test
  void refreshRacingLogoutNeverLeavesLiveTokens() throws Exception {
    final Session session = desktop();
    List<Object> results = race(() -> lifecycle.refresh(session.refresh()), () -> {
      lifecycle.logout(principal(session));
      return "logged-out";
    });
    assertFalse(results.stream().anyMatch(value -> value instanceof Exception
        && !(value instanceof ApiException)));
    assertEquals(TokenFamilyEntity.Status.REVOKED,
        families.findById(session.familyId()).orElseThrow().getStatus());
    assertEquals(0, activeTokenCount(session.familyId()));
    me(session.access(), 401);
  }

  @Test
  void refreshRacingReloginCannotRevokeNewLoginFamily() throws Exception {
    final Session old = desktop();
    List<Object> results = race(() -> lifecycle.refresh(old.refresh()),
        () -> login.exchange(request(old.installation())));
    var current = results.stream().filter(com.capstone.backend.auth.dto.AuthDtos.LoginResponse.class
        ::isInstance).map(com.capstone.backend.auth.dto.AuthDtos.LoginResponse.class::cast)
        .findFirst().orElseThrow();
    me(current.accessToken(), 200);
    me(old.access(), 401);
    assertEquals(0, activeTokenCount(old.familyId()));
    var claims = jwt.verify(current.accessToken());
    assertEquals(1, activeTokenCount(claims.tokenFamilyId()));
    assertEquals(old.deviceId(), claims.deviceId());
  }

  @Test
  void logoutReleasesDesktopSlotAndMissingBearerIsRejected() throws Exception {
    final Session first = desktop();
    desktop();
    desktop();
    assertEquals("AUTH_DEVICE_LIMIT_EXCEEDED",
        assertThrows(ApiException.class, this::desktop).code().name());
    lifecycle.logout(principal(first));
    me(desktop().access(), 200);
    mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
  }

  @Test
  void tokenDtoStringsNeverExposeSecrets() {
    String secret = "synthetic-private-value";
    assertFalse(new RefreshRequest(secret).toString().contains(secret));
    assertFalse(new TokenPair(secret, secret, Instant.now()).toString().contains(secret));
  }

  private Session desktop() {
    String installation = "install-" + UUID.randomUUID();
    var response = login.exchange(request(installation));
    var claims = jwt.verify(response.accessToken());
    return new Session(response.accessToken(), response.refreshToken(), claims.userId(),
        claims.deviceId(), claims.tokenFamilyId(), installation);
  }

  private GoogleExchangeRequest request(String installation) {
    return new GoogleExchangeRequest("synthetic-code", "a".repeat(43), subject,
        "http://127.0.0.1:49152/", new DesktopDevice(installation, "QA Desktop", "MACOS"));
  }

  private Session extension(Session desktop) {
    return tx.execute(status -> {
      var device = new UserDeviceEntity();
      device.setUser(users.findById(desktop.userId()).orElseThrow());
      device.setDeviceType(UserDeviceEntity.DeviceType.CHROME_EXTENSION);
      device.setDisplayName("QA Extension");
      device.setInstallationIdHash(TokenSecrets.hash(UUID.randomUUID().toString()));
      device.setLinkedDesktopDevice(devices.findById(desktop.deviceId()).orElseThrow());
      devices.saveAndFlush(device);
      var family = new TokenFamilyEntity();
      family.setUser(device.getUser());
      family.setDevice(device);
      family.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
      families.saveAndFlush(family);
      String raw = TokenSecrets.newRefreshToken();
      var record = new RefreshTokenRecordEntity();
      record.setTokenFamily(family);
      record.setTokenHash(TokenSecrets.hash(raw));
      record.setExpiresAt(family.getExpiresAt());
      tokens.saveAndFlush(record);
      return new Session(jwt.issue(desktop.userId(), device.getId(), family.getId()).token(), raw,
          desktop.userId(), device.getId(), family.getId(), "synthetic-extension");
    });
  }

  private FocusOnPrincipal principal(Session session) {
    return new FocusOnPrincipal(session.userId(), session.deviceId(), session.familyId(),
        UserDeviceEntity.DeviceType.DESKTOP);
  }

  private void expireFamily(long id) {
    Instant now = Instant.now();
    jdbc.update("update token_families set created_at=?,expires_at=? where id=?",
        Timestamp.from(now.minusSeconds(600)), Timestamp.from(now.minusSeconds(1)), id);
  }

  private int activeTokenCount(long id) {
    return jdbc.queryForObject("select count(*) from refresh_token_records "
        + "where token_family_id=? and status='ACTIVE'", Integer.class, id);
  }

  private int tokenCount(long id) {
    return jdbc.queryForObject("select count(*) from refresh_token_records "
        + "where token_family_id=?", Integer.class, id);
  }

  private String body(String token) {
    return "{\"refreshToken\":\"" + token + "\"}";
  }

  private void refreshError(String raw, String code) throws Exception {
    mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(body(raw))).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  private void me(String access, int expected) throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
        .andExpect(status().is(expected));
  }

  private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      var tasks = List.of(first, second).stream().map(action -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("race start timeout");
        }
        try {
          return action.call();
        } catch (Exception exception) {
          return exception;
        }
      })).toList();
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      return List.of(tasks.get(0).get(20, TimeUnit.SECONDS),
          tasks.get(1).get(20, TimeUnit.SECONDS));
    }
  }

  private record Session(String access, String refresh, long userId, long deviceId,
      long familyId, String installation) {
    @Override
    public String toString() {
      return "Session[tokens=<redacted>]";
    }
  }
}
