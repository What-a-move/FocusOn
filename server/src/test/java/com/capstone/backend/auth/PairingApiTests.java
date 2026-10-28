package com.capstone.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.PairingDtos.ClaimRequest;
import com.capstone.backend.auth.dto.PairingDtos.IssuedCode;
import com.capstone.backend.auth.dto.PairingDtos.LinkedExtension;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.auth.service.PairingSecrets;
import com.capstone.backend.auth.service.PairingService;
import com.capstone.backend.auth.service.TokenLifecycleService;
import com.capstone.backend.auth.service.TokenSecrets;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.api.RequestIdFilter;
import com.capstone.backend.user.service.OnboardingReadinessService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/** HTTP와 commit 이후 상태·동시 소비를 실제 저장소로 검증한다. Google 통신만 대체한다. */
@SpringBootTest
@ActiveProfiles("test")
class PairingApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private PairingService pairing;
  @Autowired private PairingSecrets secrets;
  @Autowired private LoginService login;
  @Autowired private TokenLifecycleService lifecycle;
  @Autowired private AccessJwtService jwt;
  @Autowired private OnboardingReadinessService readiness;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private Clock pairingClock;
  private Instant now;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    // H2/PostgreSQL은 DB 시각을 마이크로초까지 저장하므로 경계 기준을 밀리초에 고정한다.
    now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    when(pairingClock.instant()).thenReturn(now);
    when(google.exchange(any())).thenAnswer(call ->
        ((GoogleExchangeRequest) call.getArgument(0)).state());
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
  }

  @Test
  void simultaneousReadinessUpdatesKeepBothDesktopMarkers() throws Exception {
    var owner = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var results = race(() -> readiness.get(owner.principal(),
        new OnboardingReadinessService.Update(true, null)),
        () -> readiness.get(owner.principal(),
            new OnboardingReadinessService.Update(null, true)));
    assertEquals(List.of("SUCCESS", "SUCCESS"), results);
    var steps = readiness.get(owner.principal(), null).steps();
    assertTrue(steps.screenAnalysisReady());
    assertTrue(steps.analysisExcludedDomainsStepCompleted());
  }

  @Test
  void readinessStopsTreatingExpiredExtensionFamilyAsLinked() {
    var owner = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var linked = claim(owner, UUID.randomUUID().toString());
    assertTrue(readiness.get(owner.principal(), null).steps().extensionLinked());
    jdbc.update("update token_families set created_at=?, expires_at=? where id=?",
        Timestamp.from(Instant.now().minusSeconds(60)),
        Timestamp.from(Instant.now().minusSeconds(1)),
        jwt.verify(linked.accessToken()).tokenFamilyId());
    assertTrue(!readiness.get(owner.principal(), null).steps().extensionLinked());
  }

  @Test
  void onboardingReadinessTracksDesktopConnectionAndExplicitEmptySkip() throws Exception {
    String subject = UUID.randomUUID().toString();
    var owner = desktop(subject, UUID.randomUUID().toString());
    String bearer = "Bearer " + owner.access();
    String path = "/api/v1/users/me/onboarding-readiness";
    mvc.perform(get(path).header("Authorization", bearer)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentStep").value("NICKNAME"));
    mvc.perform(put("/api/v1/users/me/profile").header("Authorization", bearer)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"nickname\":\"q" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
        .andExpect(status().isOk());
    mvc.perform(get(path).header("Authorization", bearer))
        .andExpect(jsonPath("$.data.currentStep").value("EXTENSION"));
    final var linked = pairing.claim(
        new ClaimRequest(pairing.issue(owner.principal()).codeDisplay(),
            UUID.randomUUID().toString()), "readiness-ip");
    for (String type : List.of("TERMS_OF_SERVICE", "PRIVACY_POLICY", "SCREEN_ANALYSIS")) {
      mvc.perform(put("/api/v1/users/me/consents/" + type).header("Authorization", bearer)
          .contentType(MediaType.APPLICATION_JSON).content("{\"isGranted\":true,"
              + "\"consentVersion\":\"2026-09-17\",\"consentSource\":\"ONBOARDING\"}"))
          .andExpect(status().isOk());
    }
    mvc.perform(put(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
        .content("{\"screenAnalysisReady\":true}"))
        .andExpect(jsonPath("$.data.currentStep").value("EXCLUDED_SITES"));
    mvc.perform(put(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
        .content("{\"analysisExcludedDomainsStepCompleted\":true}"))
        .andExpect(jsonPath("$.data.onboardingStatus").value("COMPLETED"));
    var otherDesktop = desktop(subject, UUID.randomUUID().toString());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + otherDesktop.access()))
        .andExpect(jsonPath("$.data.onboardingStatus").value("INCOMPLETE"));
    mvc.perform(get("/api/v1/users/me").header("Authorization", bearer))
        .andExpect(jsonPath("$.data.onboardingStatus").value("COMPLETED"));
    mvc.perform(put("/api/v1/users/me/profile").header("Authorization", bearer)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"nickname\":\"q" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
        .andExpect(jsonPath("$.data.onboardingStatus").value("COMPLETED"));
    mvc.perform(get("/api/v1/users/me")
        .header("Authorization", "Bearer " + linked.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.onboardingStatus").value("COMPLETED"));
    mvc.perform(put("/api/v1/users/me/consents/SCREEN_ANALYSIS").header("Authorization", bearer)
        .contentType(MediaType.APPLICATION_JSON).content("{\"isGranted\":false,"
            + "\"consentVersion\":\"2026-09-17\",\"consentSource\":\"SETTINGS\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/users/me").header("Authorization", bearer))
        .andExpect(jsonPath("$.data.onboardingStatus").value("INCOMPLETE"));
    mvc.perform(delete("/api/v1/extension-pairings/" + linked.device().deviceId())
        .header("Authorization", bearer)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/users/me").header("Authorization", bearer))
        .andExpect(jsonPath("$.data.onboardingStatus").value("INCOMPLETE"));
    mvc.perform(get(path).header("Authorization", bearer))
        .andExpect(jsonPath("$.data.currentStep").value("EXTENSION"))
        .andExpect(jsonPath("$.data.steps.extensionLinked").value(false));
  }

  @Test
  void desktopListsOnlyItsOwnDevicesForTokenLossRecovery() throws Exception {
    var owner = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var other = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    String installation = UUID.randomUUID().toString();
    var linked = pairing.claim(new ClaimRequest(pairing.issue(owner.principal()).codeDisplay(),
        installation), "list-ip");
    var result = mvc.perform(get("/api/v1/devices")
        .header("Authorization", "Bearer " + owner.access())).andExpect(status().isOk())
        .andReturn();
    var items = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString())
        .path("data").path("devices");
    assertEquals(2, items.size());
    assertTrue(items.toString().contains(linked.device().deviceId()));
    assertTrue(!items.toString().contains(other.principal().deviceId() + "\""));
    mvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + linked.accessToken()))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/extension-pairings/" + linked.device().deviceId())
        .header("Authorization", "Bearer " + owner.access())).andExpect(status().isNoContent());
    var recovered = pairing.claim(new ClaimRequest(pairing.issue(other.principal()).codeDisplay(),
        installation), "list-ip");
    assertEquals("EXTENSION", recovered.device().deviceType());
  }

  @Test
  void extensionCanUnlinkItselfAndAnotherAccountCanThenConnect() throws Exception {
    var first = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var second = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    String installation = UUID.randomUUID().toString();
    var original = pairing.claim(new ClaimRequest(pairing.issue(first.principal()).codeDisplay(),
        installation), "unlink-ip");
    var newCode = pairing.issue(second.principal());
    assertEquals(ApiErrorCode.LINK_007_INSTALLATION_ALREADY_LINKED,
        assertThrows(ApiException.class, () -> pairing.claim(
            new ClaimRequest(newCode.codeDisplay(), installation), "unlink-ip")).code());
    mvc.perform(delete("/api/v1/extension-pairings/" + original.device().deviceId())
        .header("Authorization", "Bearer " + second.access()))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/extension-pairings/" + original.device().deviceId())
        .header("Authorization", "Bearer " + original.accessToken()))
        .andExpect(status().isNoContent()).andExpect(content().string(""));
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + original.accessToken()))
        .andExpect(status().isUnauthorized());
    assertThrows(ApiException.class, () -> lifecycle.refresh(original.refreshToken()));
    var next = pairing.claim(new ClaimRequest(newCode.codeDisplay(), installation), "unlink-ip");
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + next.accessToken()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + first.access()))
        .andExpect(status().isOk());
  }

  @Test
  void desktopCanUnlinkItsExtensionWithoutRevokingOtherExtensions() throws Exception {
    var owner = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var one = pairing.claim(new ClaimRequest(pairing.issue(owner.principal()).codeDisplay(),
        UUID.randomUUID().toString()), "unlink-ip");
    var two = pairing.claim(new ClaimRequest(pairing.issue(owner.principal()).codeDisplay(),
        UUID.randomUUID().toString()), "unlink-ip");
    mvc.perform(delete("/api/v1/extension-pairings/" + two.device().deviceId())
        .header("Authorization", "Bearer " + one.accessToken())).andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/extension-pairings/" + one.device().deviceId())
        .header("Authorization", "Bearer " + owner.access())).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + two.accessToken()))
        .andExpect(status().isOk());
  }

  @Test
  void publicClaimIssuesIndependentTokensAndDesktopLogoutRevokesThem() throws Exception {
    final var desktop = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var issued = pairing.issue(desktop.principal());
    var result = mvc.perform(post("/api/v1/extension-pairings")
        .header("Authorization", "Bearer deliberately-invalid")
        .contentType(MediaType.APPLICATION_JSON).content(JsonMapper.builder().build()
            .writeValueAsString(
                new ClaimRequest(issued.codeDisplay(), UUID.randomUUID().toString()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.device.deviceType").value("EXTENSION"))
        .andExpect(jsonPath("$.data.accessTokenExpiresAt").exists()).andReturn();
    var data = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString())
        .get("data");
    String access = data.get("accessToken").asText();
    String refresh = data.get("refreshToken").asText();
    assertNotEquals(desktop.principal().tokenFamilyId(), jwt.verify(access).tokenFamilyId());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
    var rotated = lifecycle.refresh(refresh);
    lifecycle.logout(desktop.principal());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + rotated.accessToken()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("LINK_TOKEN_REVOKED"));
  }

  @Test
  void desktopAndExtensionCanExchangeTheirDocumentedJsonFields() throws Exception {
    var desktop = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var issuedResponse = mvc.perform(post("/api/v1/pairing-codes")
        .header("Authorization", "Bearer " + desktop.access()))
        .andExpect(status().isCreated()).andReturn();
    var json = JsonMapper.builder().build();
    var issued = json.readTree(issuedResponse.getResponse().getContentAsString()).path("data");
    assertTrue(issued.path("pairingCodeId").isString());
    String display = issued.path("codeDisplay").asText();
    assertTrue(display.matches("[A-Z0-9]{3}-[A-Z0-9]{3}"));
    assertTrue(Instant.parse(issued.path("expiresAt").asText()).isAfter(now));
    assertEquals(3, issued.path("maxAttempts").asInt());

    // Extension이 실제로 전송하는 하이픈 없는 코드와 installationId를 사용한다.
    String body = json.writeValueAsString(new ClaimRequest(
        display.replace("-", ""), UUID.randomUUID().toString()));
    var linkedResponse = mvc.perform(post("/api/v1/extension-pairings")
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn();
    var linked = json.readTree(linkedResponse.getResponse().getContentAsString()).path("data");
    assertTrue(linked.path("accessToken").isString());
    assertTrue(linked.path("refreshToken").isString());
    assertTrue(Instant.parse(linked.path("accessTokenExpiresAt").asText()).isAfter(now));
    assertTrue(linked.path("device").path("deviceId").isString());
    assertEquals("EXTENSION", linked.path("device").path("deviceType").asText());

    // Desktop이 보관할 발급 ID로 소비된 코드를 취소해도 연결은 유지된다.
    mvc.perform(delete("/api/v1/pairing-codes/" + issued.path("pairingCodeId").asText())
        .header("Authorization", "Bearer " + desktop.access())).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").header("Authorization",
        "Bearer " + linked.path("accessToken").asText())).andExpect(status().isOk());
  }

  @Test
  void invalidAttemptsCommitAndLockCorrectCodeUntilExactThreeMinuteBoundary() throws Exception {
    final var desktop = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var code = pairing.issue(desktop.principal());
    String installation = UUID.randomUUID().toString();
    String wrong = unusedCode();
    for (int remaining = 2; remaining >= 0; remaining--) {
      var error = assertThrows(ApiException.class,
          () -> pairing.claim(new ClaimRequest(wrong, installation), "lock-ip"));
      assertEquals(remaining, error.details().get("remainingAttempts"));
      assertEquals(remaining == 0 ? ApiErrorCode.LINK_004_ATTEMPTS_EXCEEDED
          : ApiErrorCode.LINK_006_CODE_MISMATCH, error.code());
    }
    mvc.perform(post("/api/v1/extension-pairings").contentType(MediaType.APPLICATION_JSON)
        .content(JsonMapper.builder().build().writeValueAsString(
            new ClaimRequest(code.codeDisplay(), installation))))
        .andExpect(status().isGone()).andExpect(header().string("Retry-After", "180"))
        .andExpect(jsonPath("$.retryAfterSeconds").value(180))
        .andExpect(jsonPath("$.retryable").value(false));
    when(pairingClock.instant()).thenReturn(now.plusSeconds(179));
    assertEquals(1L, assertThrows(ApiException.class, () -> pairing.claim(
        new ClaimRequest(code.codeDisplay(), installation), "lock-ip")).retryAfterSeconds());
    when(pairingClock.instant()).thenReturn(now.plusSeconds(180));
    assertEquals("EXTENSION", pairing.claim(new ClaimRequest(code.codeDisplay(), installation),
        "lock-ip").device().deviceType());
  }

  @Test
  void cleanupExpiresUnfinishedFailuresAfterTwentyFourHours() {
    final var desktop = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    String installation = UUID.randomUUID().toString();
    String wrong = unusedCode();
    assertEquals(2, assertThrows(ApiException.class, () -> pairing.claim(
        new ClaimRequest(wrong, installation), "reset-ip")).details().get("remainingAttempts"));
    jdbc.update("update pairing_installation_attempts set updated_at=? "
        + "where installation_id_hash=?",
        Timestamp.from(now.minusSeconds(40L * 86400)), TokenSecrets.hash(installation));
    pairing.cleanup();
    assertEquals(2, assertThrows(ApiException.class, () -> pairing.claim(
        new ClaimRequest(wrong, installation), "reset-ip")).details().get("remainingAttempts"));
    var code = pairing.issue(desktop.principal());
    pairing.claim(new ClaimRequest(code.codeDisplay(), installation), "reset-ip");
    assertEquals(2, assertThrows(ApiException.class, () -> pairing.claim(
        new ClaimRequest(wrong, installation), "reset-ip")).details().get("remainingAttempts"));
  }

  @Test
  void unfinishedFailuresExpireAtExactTwentyFourHourBoundaryWithoutCleanup() {
    String installation = UUID.randomUUID().toString();
    String wrong = unusedCode();
    assertEquals(2, assertThrows(ApiException.class, () -> pairing.claim(
        new ClaimRequest(wrong, installation), "expiry-window-ip"))
        .details().get("remainingAttempts"));
    when(pairingClock.instant()).thenReturn(now.plusSeconds(24L * 3600 - 1));
    assertEquals(1, assertThrows(ApiException.class, () -> pairing.claim(
        new ClaimRequest(wrong, installation), "expiry-window-ip"))
        .details().get("remainingAttempts"));
    // 두 번째 실패 시각부터 다시 24시간을 센다.
    when(pairingClock.instant()).thenReturn(now.plusSeconds(2L * 24 * 3600 - 1));
    assertEquals(2, assertThrows(ApiException.class, () -> pairing.claim(
        new ClaimRequest(wrong, installation), "expiry-window-ip"))
        .details().get("remainingAttempts"));
  }

  @Test
  void sameUserReusesDeviceAndRevokesPreviousTokensOtherUserDoesNotConsumeCode() throws Exception {
    String subject = UUID.randomUUID().toString();
    var first = desktop(subject, UUID.randomUUID().toString());
    var second = desktop(subject, UUID.randomUUID().toString());
    String installation = UUID.randomUUID().toString();
    var old = claim(first, installation);
    var next = claim(second, installation);
    assertEquals(old.device().deviceId(), next.device().deviceId());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + old.accessToken()))
        .andExpect(status().isUnauthorized());
    assertEquals(ApiErrorCode.AUTH_TOKEN_REUSE_DETECTED,
        assertThrows(ApiException.class, () -> lifecycle.refresh(old.refreshToken())).code());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + next.accessToken()))
        .andExpect(status().isOk());
    var other = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var code = pairing.issue(other.principal());
    assertEquals(ApiErrorCode.LINK_007_INSTALLATION_ALREADY_LINKED,
        assertThrows(ApiException.class, () -> pairing.claim(
            new ClaimRequest(code.codeDisplay(), installation), "other-ip")).code());
    assertEquals("ACTIVE", codeStatus(code));
    // 이전 Desktop의 logout은 재연결 후 새 Desktop의 Extension에 영향을 주지 않는다.
    lifecycle.logout(first.principal());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + next.accessToken()))
        .andExpect(status().isOk());
  }

  @Test
  void cancellationIsOwnedIdempotentAndDoesNotUnlinkConsumedCode() throws Exception {
    var first = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var other = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var code = pairing.issue(first.principal());
    pairing.cancel(other.principal(), Long.parseLong(code.pairingCodeId()));
    assertEquals("ACTIVE", codeStatus(code));
    mvc.perform(delete("/api/v1/pairing-codes/" + code.pairingCodeId())
        .header("Authorization", "Bearer " + first.access()))
        .andExpect(status().isNoContent()).andExpect(content().string(""));
    pairing.cancel(first.principal(), Long.parseLong(code.pairingCodeId()));
    assertEquals(ApiErrorCode.LINK_005_CANCELLED, assertThrows(ApiException.class,
        () -> pairing.claim(new ClaimRequest(code.codeDisplay(), UUID.randomUUID().toString()),
            "cancel-ip")).code());
    var consumed = pairing.issue(first.principal());
    var linked = pairing.claim(
        new ClaimRequest(consumed.codeDisplay(), UUID.randomUUID().toString()),
        "cancel-ip");
    pairing.cancel(first.principal(), Long.parseLong(consumed.pairingCodeId()));
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + linked.accessToken()))
        .andExpect(status().isOk());
    pairing.cancel(first.principal(), Long.MAX_VALUE);
  }

  @Test
  void reissueReloginAndLogoutCancelPreviouslyIssuedCodes() {
    String subject = UUID.randomUUID().toString();
    String installation = UUID.randomUUID().toString();
    var first = desktop(subject, installation);
    var old = pairing.issue(first.principal());
    var next = pairing.issue(first.principal());
    assertEquals("CANCELLED", codeStatus(old));
    var relogged = desktop(subject, installation);
    assertEquals("CANCELLED", codeStatus(next));
    var last = pairing.issue(relogged.principal());
    lifecycle.logout(relogged.principal());
    assertEquals("CANCELLED", codeStatus(last));
  }

  @Test
  void expiredCodeCommitsTerminalTimeAndCleanupUsesExpiryNotWorkerTime() {
    final var desktop = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var code = pairing.issue(desktop.principal());
    when(pairingClock.instant()).thenReturn(now.plusSeconds(300));
    assertEquals(ApiErrorCode.LINK_002_EXPIRED, assertThrows(ApiException.class,
        () -> pairing.claim(new ClaimRequest(code.codeDisplay(), UUID.randomUUID().toString()),
            "expiry-ip")).code());
    assertEquals("EXPIRED", codeStatus(code));
    when(pairingClock.instant()).thenReturn(now.plusSeconds(300 + 30L * 86400));
    pairing.cleanup();
    assertEquals(0, jdbc.queryForObject("select count(*) from pairing_codes where id=?",
        Integer.class, Long.valueOf(code.pairingCodeId())));
  }

  @Test
  void codeFormatAndProtectedMethodsHaveExpectedErrors() throws Exception {
    mvc.perform(post("/api/v1/extension-pairings").contentType(MediaType.APPLICATION_JSON)
        .content("{\"pairingCode\":\"bad\",\"installationId\":\"format-test\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LINK_001_INVALID_FORMAT"));
    mvc.perform(post("/api/v1/pairing-codes")).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/pairing-codes/1")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/extension-pairings")).andExpect(status().isUnauthorized());
    assertEquals("ABC123", secrets.normalize(" abc-123 "));
    assertNotEquals(TokenSecrets.hash("ABC123"), secrets.hash("ABC123"));
  }

  @Test
  void concurrentCodeClaimsHaveExactlyOneWinner() throws Exception {
    final var desktop = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var code = pairing.issue(desktop.principal());
    String installation = UUID.randomUUID().toString();
    var start = new CountDownLatch(1);
    Callable<String> task = () -> {
      start.await();
      try {
        pairing.claim(new ClaimRequest(code.codeDisplay(), installation), "race-ip");
        return "SUCCESS";
      } catch (ApiException error) {
        return error.code().name();
      }
    };
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(task);
      var b = executor.submit(task);
      start.countDown();
      var outcomes = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
      assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
      assertTrue(outcomes.contains("LINK_003_ALREADY_USED"));
    }
  }

  @Test
  void expiredCodesKeepExpiryWhenReissuedLoggedOutOrRelogged() {
    String subject = UUID.randomUUID().toString();
    String installation = UUID.randomUUID().toString();
    var desktop = desktop(subject, installation);
    var old = pairing.issue(desktop.principal());
    ageCode(old);
    var next = pairing.issue(desktop.principal());
    assertEquals("EXPIRED", codeStatus(old));
    ageCode(next);
    var relogged = desktop(subject, installation);
    assertEquals("EXPIRED", codeStatus(next));
    var last = pairing.issue(relogged.principal());
    ageCode(last);
    lifecycle.logout(relogged.principal());
    assertEquals("EXPIRED", codeStatus(last));
    assertEquals(0, jdbc.queryForObject("select count(*) from pairing_codes "
        + "where status='EXPIRED' and terminal_at<>expires_at", Integer.class));
  }

  @Test
  void sameInstallationAcrossUsersHasOneWinnerAndLeavesLoserCodeActive() throws Exception {
    var first = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var other = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var a = pairing.issue(first.principal());
    var b = pairing.issue(other.principal());
    String installation = UUID.randomUUID().toString();
    var results = race(() -> pairing.claim(new ClaimRequest(a.codeDisplay(), installation), "a-ip"),
        () -> pairing.claim(new ClaimRequest(b.codeDisplay(), installation), "b-ip"));
    assertEquals(1, results.stream().filter("SUCCESS"::equals).count());
    assertTrue(results.contains("LINK_007_INSTALLATION_ALREADY_LINKED"));
    assertEquals(1, List.of(codeStatus(a), codeStatus(b)).stream()
        .filter("ACTIVE"::equals).count());
  }

  @Test
  void cancellationAndClaimRaceCannotResurrectOrRevokeConsumedExtension() throws Exception {
    var desktop = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var code = pairing.issue(desktop.principal());
    var results = race(() -> pairing.claim(new ClaimRequest(code.codeDisplay(),
        UUID.randomUUID().toString()), "cancel-race-ip"), () -> {
          pairing.cancel(desktop.principal(), Long.valueOf(code.pairingCodeId()));
          return null;
        });
    assertTrue(results.stream().allMatch(value -> value.equals("SUCCESS")
        || value.equals("LINK_005_CANCELLED")));
    assertTrue(List.of("CONSUMED", "CANCELLED").contains(codeStatus(code)));
  }

  @Test
  void logoutAndClaimRaceAlwaysLeavesExtensionUnauthenticated() throws Exception {
    var desktop = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var code = pairing.issue(desktop.principal());
    String installation = UUID.randomUUID().toString();
    var results = race(() -> pairing.claim(new ClaimRequest(code.codeDisplay(), installation),
        "logout-race-ip"), () -> {
          lifecycle.logout(desktop.principal());
          return null;
        });
    assertTrue(results.stream().allMatch(value -> value.equals("SUCCESS")
        || value.equals("LINK_005_CANCELLED")));
    assertEquals(0, jdbc.queryForObject("select count(*) from user_devices "
        + "where installation_id_hash=? and status='ACTIVE'", Integer.class,
        TokenSecrets.hash(installation)));
  }

  @Test
  void issuingLimitIsThreeAndExtensionCannotIssueOrCancel() throws Exception {
    var desktop = desktop(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var code = pairing.issue(desktop.principal());
    final var linked = pairing.claim(
        new ClaimRequest(code.codeDisplay(), UUID.randomUUID().toString()),
        "extension-scope-ip");
    pairing.issue(desktop.principal());
    pairing.issue(desktop.principal());
    assertEquals(ApiErrorCode.TOO_MANY_REQUESTS,
        assertThrows(ApiException.class, () -> pairing.issue(desktop.principal())).code());
    mvc.perform(post("/api/v1/pairing-codes")
        .header("Authorization", "Bearer " + linked.accessToken()))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/pairing-codes/" + code.pairingCodeId())
        .header("Authorization", "Bearer " + linked.accessToken()))
        .andExpect(status().isUnauthorized());
  }

  private void ageCode(IssuedCode code) {
    jdbc.update("update pairing_codes set created_at=?, expires_at=? where id=?",
        Timestamp.from(now.minusSeconds(600)), Timestamp.from(now.minusSeconds(1)),
        Long.valueOf(code.pairingCodeId()));
  }

  private List<String> race(Callable<?> first, Callable<?> second) throws Exception {
    var start = new CountDownLatch(1);
    Callable<String> a = () -> runAfter(start, first);
    Callable<String> b = () -> runAfter(start, second);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var left = executor.submit(a);
      var right = executor.submit(b);
      start.countDown();
      return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
    }
  }

  private String runAfter(CountDownLatch start, Callable<?> task) throws Exception {
    start.await();
    try {
      task.call();
      return "SUCCESS";
    } catch (ApiException error) {
      return error.code().name();
    }
  }

  private LinkedExtension claim(Desktop desktop, String installation) {
    var code = pairing.issue(desktop.principal());
    return pairing.claim(new ClaimRequest(code.codeDisplay(), installation),
        UUID.randomUUID().toString());
  }

  private Desktop desktop(String subject, String installation) {
    var result = login.exchange(new GoogleExchangeRequest("test-code", "v".repeat(43), subject,
        "http://127.0.0.1:45678", new DesktopDevice(installation, "QA Desktop", "MACOS")));
    var token = jwt.verify(result.accessToken());
    return new Desktop(new FocusOnPrincipal(token.userId(), token.deviceId(), token.tokenFamilyId(),
        UserDeviceEntity.DeviceType.DESKTOP), result.accessToken());
  }

  private String unusedCode() {
    String code;
    do {
      code = secrets.generate();
    } while (jdbc.queryForObject("select count(*) from pairing_codes where code_hash=?",
        Integer.class, secrets.hash(code)) != 0);
    return code;
  }

  private String codeStatus(IssuedCode code) {
    return jdbc.queryForObject("select status from pairing_codes where id=?", String.class,
        Long.valueOf(code.pairingCodeId()));
  }

  private record Desktop(FocusOnPrincipal principal, String access) {}
}
