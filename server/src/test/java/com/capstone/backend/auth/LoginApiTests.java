package com.capstone.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.persistence.RefreshTokenRecordRepository;
import com.capstone.backend.auth.security.RequestAuthenticationService;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.auth.service.TokenSecrets;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.api.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Google 통신만 대체하고 Controller·필터·DB transaction은 실제 Spring 경로로 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class LoginApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private AccessJwtService jwt;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired private com.capstone.backend.auth.persistence.UserDeviceRepository devices;
  @Autowired private RequestAuthenticationService authentication;
  @Autowired private RefreshTokenRecordRepository tokens;
  @MockitoBean private GoogleOauthClient google;
  private MockMvc mvc;
  private String subject;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    subject = "synthetic-sub-" + UUID.randomUUID();
    when(google.exchange(any())).thenReturn(subject);
  }

  @Test
  void exchangeReturnsDesktopContractAndMeUsesVerifiedPrincipal() throws Exception {
    var response = login.exchange(request("installation-a"));
    assertEquals("DESKTOP", response.device().deviceType());
    assertEquals("INCOMPLETE", response.onboardingStatus());
    assertEquals(64, tokens.findByTokenHash(TokenSecrets.hash(response.refreshToken()))
        .orElseThrow().getTokenHash().length());
    mvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer " + response.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.userId").value(response.user().userId()))
        .andExpect(jsonPath("$.data.device.deviceId").value(response.device().deviceId()));
    mvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer " + response.accessToken())
            .header("X-Device-Id", "999999"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_RELOGIN_REQUIRED"));
  }

  @Test
  void exchangeHttpReturns201WithDesktopFields() throws Exception {
    String body = """
        {"authorizationCode":"synthetic-code","codeVerifier":"%s","state":"synthetic-state",
         "redirectUri":"http://127.0.0.1:49152","device":{"installationId":"http-install",
         "deviceName":"QA Desktop","platform":"MACOS"}}
        """.formatted("a".repeat(43));
    mvc.perform(post("/api/v1/auth/google/exchange")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.data.accessTokenExpiresAt").isString())
        .andExpect(jsonPath("$.data.user.userId").isString())
        .andExpect(jsonPath("$.data.device.deviceType").value("DESKTOP"))
        .andExpect(jsonPath("$.data.onboardingStatus").value("INCOMPLETE"));
  }

  @Test
  void sameInstallationReusesDeviceAndRevokesPreviousFamily() {
    var first = login.exchange(request("same-installation"));
    var second = login.exchange(request("same-installation"));
    assertEquals(first.user().userId(), second.user().userId());
    assertEquals(first.device().deviceId(), second.device().deviceId());
    assertNotEquals(first.refreshToken(), second.refreshToken());
    assertThrows(ApiException.class, () -> authentication.authenticate(first.accessToken()));
    assertEquals(Long.parseLong(second.user().userId()),
        authentication.authenticate(second.accessToken()).userId());
  }

  @Test
  void fourthDesktopIsRejectedWithoutRevokingExistingDevices() {
    final var first = login.exchange(request("desktop-1"));
    login.exchange(request("desktop-2"));
    login.exchange(request("desktop-3"));
    ApiException blocked = assertThrows(ApiException.class,
        () -> login.exchange(request("desktop-4")));
    assertEquals("AUTH_DEVICE_LIMIT_EXCEEDED", blocked.code().name());
    assertEquals(3, ((java.util.List<?>) blocked.details().get("devices")).size());
    authentication.authenticate(first.accessToken());
    login.exchange(request("desktop-1"));
  }

  @Test
  void simultaneousFirstLoginHonorsDesktopLimit() throws Exception {
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var work = new java.util.ArrayList<java.util.concurrent.Future<String>>();
      for (int i = 0; i < 4; i++) {
        final int index = i;
        work.add(pool.submit(() -> {
          start.await();
          try {
            return login.exchange(request("concurrent-" + index)).user().userId();
          } catch (ApiException exception) {
            if (exception.code().name().equals("AUTH_DEVICE_LIMIT_EXCEEDED")) {
              return "blocked";
            }
            throw exception;
          }
        }));
      }
      start.countDown();
      var userIds = new java.util.HashSet<String>();
      int blocked = 0;
      for (var future : work) {
        String result = future.get(15, java.util.concurrent.TimeUnit.SECONDS);
        if (result.equals("blocked")) {
          blocked++;
        } else {
          userIds.add(result);
        }
      }
      assertEquals(1, userIds.size());
      assertEquals(1, blocked);
    }
  }

  @Test
  void rejectsValidSignatureWithDifferentOwnerAndRevokedDevice() throws Exception {
    var issued = login.exchange(request("ownership-check"));
    var claims = jwt.verify(issued.accessToken());
    String wrongOwner = jwt.issue(claims.userId() + 100000, claims.deviceId(),
        claims.tokenFamilyId()).token();
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + wrongOwner))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_RELOGIN_REQUIRED"));
    var device = devices.findById(claims.deviceId()).orElseThrow();
    device.setStatus(com.capstone.backend.auth.persistence.UserDeviceEntity.Status.REVOKED);
    device.setRevokedAt(java.time.Instant.now());
    devices.saveAndFlush(device);
    mvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer " + issued.accessToken()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_RELOGIN_REQUIRED"));
  }

  @Test
  void rejectsExpiredFamilyEvenWhenAccessTokenHasNotExpired() throws Exception {
    var issued = login.exchange(request("expired-family-check"));
    var claims = jwt.verify(issued.accessToken());
    // ACTIVE 상태를 유지한 채 실제 만료 시각만 과거로 두어 시간 검사를 검증한다.
    java.time.Instant now = java.time.Instant.now();
    jdbc.update("update token_families set created_at = ?, expires_at = ? where id = ?",
        java.sql.Timestamp.from(now.minusSeconds(600)),
        java.sql.Timestamp.from(now.minusSeconds(1)), claims.tokenFamilyId());
    mvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer " + issued.accessToken()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_RELOGIN_REQUIRED"));
  }

  @Test
  void invalidInputAndMissingBearerUseCommonEnvelope() throws Exception {
    mvc.perform(post("/api/v1/auth/google/exchange").contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_RELOGIN_REQUIRED"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  private GoogleExchangeRequest request(String installation) {
    return new GoogleExchangeRequest("synthetic-code", "a".repeat(43), "synthetic-state",
        "http://127.0.0.1:49152", new DesktopDevice(installation, "QA Desktop", "MACOS"));
  }
}
