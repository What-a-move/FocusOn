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

  /** 테스트별 사용자 식별자를 분리하고 Google 응답만 대체해 실제 저장·인증 흐름을 사용한다. */
  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    tx = new TransactionTemplate(transactions);
    subject = "lifecycle-" + UUID.randomUUID();
    when(google.exchange(any())).thenAnswer(call ->
        ((GoogleExchangeRequest) call.getArgument(0)).state());
  }

  /** 만료 Bearer를 무시하고 회전·부모 이력·30일 연장·활성 token 1개를 함께 검증한다. */
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

  /** 실제 서명된 만료 JWT가 refresh와 me를 거쳐 정상 로그인으로 복구되는지 확인한다. */
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

  /** 재사용 401 이후 별도 조회로 폐기 commit과 다른 Desktop의 격리를 확인한다. */
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

  /** 누락·공백·미확인 token의 401과 과도한 길이의 400 계약을 검증한다. */
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

  /** token 또는 family 만료 뒤에는 새 token을 만들지 못하는지 확인한다. */
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

  /** 삭제 사용자나 폐기 기기는 token을 가지고 있어도 갱신하지 못하는지 확인한다. */
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

  /** 동일 token 경쟁에서 한 번만 회전하고 후속 재사용이 family를 폐기하는지 확인한다. */
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

  /** 현재 Desktop·연결 Extension 폐기와 다른 로그인 유지 및 기존 폐기 사유 보존을 검증한다. */
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

  /** Extension의 독립 token 갱신을 허용하면서 Desktop 로그아웃 호출은 거절한다. */
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

  /** 부모 Desktop의 실제 로그인 family가 만료되면 Extension 인증·갱신도 거절한다. */
  @Test
  void extensionCannotRefreshWithoutLiveOwnedDesktopFamily() throws Exception {
    final Session desktop = desktop();
    final Session child = extension(desktop);
    expireFamily(desktop.familyId());
    refreshError(child.refresh(), "AUTH_RELOGIN_REQUIRED");
    me(child.access(), 401);
    assertEquals(1, tokenCount(child.familyId()));
  }

  /** 연결 Desktop이 없는 Extension은 유효 token을 갖고 있어도 인증하지 않는다. */
  @Test
  void extensionWithMissingParentIsRejected() throws Exception {
    final Session child = extension(desktop());
    jdbc.update("update user_devices set linked_desktop_device_id=null where id=?",
        child.deviceId());
    refreshError(child.refresh(), "AUTH_RELOGIN_REQUIRED");
    me(child.access(), 401);
  }

  /** 회전과 로그아웃 순서가 달라도 최종적으로 사용 가능한 token이 남지 않게 한다. */
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

  /** 같은 설치의 재로그인과 회전이 경쟁해도 과거 token으로 새 family를 폐기하지 않는다. */
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

  /** 로그아웃의 인증 요구와 활성 Desktop 3대 슬롯 반환을 검증한다. */
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

  /** DTO 문자열 변환에 Access·Refresh Token 원문이 포함되지 않게 한다. */
  @Test
  void tokenDtoStringsNeverExposeSecrets() {
    String secret = "synthetic-private-value";
    assertFalse(new RefreshRequest(secret).toString().contains(secret));
    assertFalse(new TokenPair(secret, secret, Instant.now()).toString().contains(secret));
  }

  /** 매번 새 설치로 실제 로그인 서비스를 호출해 테스트용 Desktop 인증 문맥을 만든다. */
  private Session desktop() {
    String installation = "install-" + UUID.randomUUID();
    var response = login.exchange(request(installation));
    var claims = jwt.verify(response.accessToken());
    return new Session(response.accessToken(), response.refreshToken(), claims.userId(),
        claims.deviceId(), claims.tokenFamilyId(), installation);
  }

  /** 합성 Google 코드와 PKCE 입력을 만들고 같은 설치 재로그인에도 재사용한다. */
  private GoogleExchangeRequest request(String installation) {
    return new GoogleExchangeRequest("synthetic-code", "a".repeat(43), subject,
        "http://127.0.0.1:49152/", new DesktopDevice(installation, "QA Desktop", "MACOS"));
  }

  /** pairing API를 구현하지 않고 전용 테스트 transaction에서 부모와 연결된 Extension을 만든다. */
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

  /** 서비스 동시성 검증에 사용할 Desktop principal을 기존 세션 ID로 구성한다. */
  private FocusOnPrincipal principal(Session session) {
    return new FocusOnPrincipal(session.userId(), session.deviceId(), session.familyId(),
        UserDeviceEntity.DeviceType.DESKTOP);
  }

  /** 대기 없이 만료 조건을 만들되 DB의 생성·만료 시각 제약을 함께 만족시킨다. */
  private void expireFamily(long id) {
    Instant now = Instant.now();
    jdbc.update("update token_families set created_at=?,expires_at=? where id=?",
        Timestamp.from(now.minusSeconds(600)), Timestamp.from(now.minusSeconds(1)), id);
  }

  /** commit된 DB 상태에서 family의 활성 Refresh Token 개수를 읽는다. */
  private int activeTokenCount(long id) {
    return jdbc.queryForObject("select count(*) from refresh_token_records "
        + "where token_family_id=? and status='ACTIVE'", Integer.class, id);
  }

  /** 실패한 갱신이 기록을 추가하지 않았는지 검사할 전체 token 수를 읽는다. */
  private int tokenCount(long id) {
    return jdbc.queryForObject("select count(*) from refresh_token_records "
        + "where token_family_id=?", Integer.class, id);
  }

  /** 테스트에서 생성한 안전한 token 문자열로 refresh JSON을 만든다. */
  private String body(String token) {
    return "{\"refreshToken\":\"" + token + "\"}";
  }

  /** 공통 401 코드와 requestId를 실제 MVC 응답에서 확인한다. */
  private void refreshError(String raw, String code) throws Exception {
    mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(body(raw))).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  /** 발급 JWT를 실제 인증 필터에 통과시켜 현재 사용 가능 여부를 확인한다. */
  private void me(String access, int expected) throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
        .andExpect(status().is(expected));
  }

  /** 두 작업을 같은 출발 신호로 실행하고 결과 또는 예외를 수집해 경쟁 결과를 검사한다. */
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
    /** 실패 메시지에 테스트 token 원문이 노출되지 않도록 마스킹한다. */
    @Override
    public String toString() {
      return "Session[tokens=<redacted>]";
    }
  }
}
