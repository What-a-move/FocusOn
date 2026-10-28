package com.capstone.backend.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.common.api.RequestIdFilter;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserConsentRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

/** 인증된 사용자 설정 API의 소유권과 도메인 정규화를 HTTP 경로로 검증한다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class UserConfigurationApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private UserConsentRepository consents;
  @org.springframework.beans.factory.annotation.Value("${spring.datasource.driver-class-name}")
  private String driverClassName;
  @MockitoBean private GoogleOauthClient google;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "user-config-" + UUID.randomUUID());
  }

  @Test
  void concurrentFirstWritesAndNicknameConflicts() throws Exception {
    LoginResponse first = desktop("concurrent-first");
    LoginResponse second = desktop("concurrent-second");
    if (!"org.h2.Driver".equals(driverClassName)) {
      // PostgreSQL의 partial UNIQUE와 제약명 매핑은 Flyway를 실행하지 않는 H2로 검증할 수 없다.
      String nickname = "qa" + UUID.randomUUID().toString().substring(0, 8);
      String profileBody = "{\"nickname\":\"" + nickname + "\"}";
      List<Integer> profileStatuses = concurrentPut("/api/v1/users/me/profile", profileBody,
          first.accessToken(), second.accessToken());
      assertEquals(List.of(200, 409), profileStatuses.stream().sorted().toList());
    }

    String consentBody = "{\"isGranted\":true,\"consentVersion\":\"2026-09-17\","
        + "\"consentSource\":\"ONBOARDING\"}";
    assertEquals(List.of(200, 200), concurrentPut("/api/v1/users/me/consents/PRIVACY_POLICY",
        consentBody, first.accessToken(), first.accessToken()));
    String focusBody = "{\"interventionLevel\":\"NORMAL\",\"notificationChannels\":[\"MOLE_UI\"],"
        + "\"breakSuggestionEnabled\":true,\"cameraAnalysisEnabled\":false}";
    assertEquals(List.of(200, 200), concurrentPut("/api/v1/users/me/focus-settings", focusBody,
        first.accessToken(), first.accessToken()));
    assertEquals(1, consents.findAllByUserIdOrderByConsentType(
        Long.parseLong(first.user().userId())).size());
  }

  private List<Integer> concurrentPut(String path, String body, String firstToken,
      String secondToken) throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = List.of(firstToken, secondToken).stream().map(token -> executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("동시 요청 시작 시간 초과");
        }
        return mvc.perform(put(path).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn()
            .getResponse().getStatus();
      })).toList();
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      return List.of(futures.get(0).get(30, TimeUnit.SECONDS),
          futures.get(1).get(30, TimeUnit.SECONDS));
    }
  }

  @Test
  void profileAndFocusSettingsUseOnlyAuthenticatedUser() throws Exception {
    LoginResponse session = desktop("profile-device");
    String nickname = "qa" + UUID.randomUUID().toString().substring(0, 8);
    mvc.perform(put("/api/v1/users/me/profile").header("Authorization",
            "Bearer " + session.accessToken()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nickname\":\"" + nickname + "\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value(nickname));
    mvc.perform(put("/api/v1/users/me/focus-settings").header("Authorization",
            "Bearer " + session.accessToken()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"interventionLevel\":\"ACTIVE\","
                + "\"notificationChannels\":[\"MOLE_UI\",\"SOUND\"],"
                + "\"breakSuggestionEnabled\":true,\"cameraAnalysisEnabled\":false}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.interventionLevel").value("ACTIVE"));
    mvc.perform(get("/api/v1/users/me/focus-settings").header("Authorization",
            "Bearer " + session.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notificationChannels[1]").value("SOUND"));
  }

  @Test
  void profileDuplicateAndConsentStateAreValidated() throws Exception {
    LoginResponse first = desktop("profile-first");
    LoginResponse second = desktop("profile-second");
    String profile = "/api/v1/users/me/profile";
    String nickname = "qa" + UUID.randomUUID().toString().substring(0, 8);
    String body = "{\"nickname\":\"" + nickname + "\"}";
    mvc.perform(put(profile).header("Authorization", "Bearer " + first.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    mvc.perform(put(profile).header("Authorization", "Bearer " + second.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROFILE_NICKNAME_DUPLICATE"));
    mvc.perform(put(profile).header("Authorization", "Bearer " + first.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROFILE_NICKNAME_INVALID"));
    mvc.perform(put(profile).header("Authorization", "Bearer " + first.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"a!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROFILE_NICKNAME_INVALID"));
    String consent = "/api/v1/users/me/consents/SCREEN_ANALYSIS";
    String granted = "{\"isGranted\":true,\"consentVersion\":\"2026-09-17\","
        + "\"consentSource\":\"DESKTOP\"}";
    String withdrawn = granted.replace("true", "false").replace("DESKTOP", "SETTINGS");
    mvc.perform(put(consent).header("Authorization", "Bearer " + first.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content(granted))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.isGranted").value(true))
        .andExpect(jsonPath("$.data.grantedAt").isNotEmpty());
    assertEquals("DESKTOP", consents.findByUserIdAndConsentType(
        Long.parseLong(first.user().userId()), ConsentType.SCREEN_ANALYSIS).orElseThrow()
        .getConsentSource());
    mvc.perform(put(consent).header("Authorization", "Bearer " + first.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content(withdrawn))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.isGranted").value(false))
        .andExpect(jsonPath("$.data.withdrawnAt").isNotEmpty());
    assertEquals("SETTINGS", consents.findByUserIdAndConsentType(
        Long.parseLong(first.user().userId()), ConsentType.SCREEN_ANALYSIS).orElseThrow()
        .getConsentSource());
    mvc.perform(get("/api/v1/users/me/consents")
            .header("Authorization", "Bearer " + second.accessToken()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.consents.length()").value(0));
  }

  @Test
  void profileAndFocusSettingsAreIsolatedBetweenUsers() throws Exception {
    LoginResponse first = desktop("isolation-first");
    LoginResponse second = desktop("isolation-second");
    String nickname = "qa" + UUID.randomUUID().toString().substring(0, 8);
    mvc.perform(put("/api/v1/users/me/profile")
            .header("Authorization", "Bearer " + first.accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nickname\":\"" + nickname + "\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/users/me")
            .header("Authorization", "Bearer " + second.accessToken()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").isEmpty());
    mvc.perform(put("/api/v1/users/me/focus-settings")
            .header("Authorization", "Bearer " + first.accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"interventionLevel\":\"ACTIVE\",\"notificationChannels\":[\"SOUND\"],"
                + "\"breakSuggestionEnabled\":false,\"cameraAnalysisEnabled\":true}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/users/me/focus-settings")
            .header("Authorization", "Bearer " + second.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.interventionLevel").value("NORMAL"))
        .andExpect(jsonPath("$.data.notificationChannels[0]").value("MOLE_UI"))
        .andExpect(jsonPath("$.data.cameraAnalysisEnabled").value(false));
    mvc.perform(get("/api/v1/users/me/focus-settings")
            .header("Authorization", "Bearer " + first.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.interventionLevel").value("ACTIVE"));
  }

  @Test
  void excludedDomainsRequireMatchingDeviceAndNormalizeHosts() throws Exception {
    LoginResponse session = desktop("domain-device");
    mvc.perform(put("/api/v1/users/me/analysis-excluded-domains").header("Authorization",
            "Bearer " + session.accessToken()).header("X-Device-Id", session.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"domains\":[\"YouTube.COM.\",\"bücher.de\",\"youtube.com\"]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.domains.length()").value(2))
        .andExpect(jsonPath("$.data.domains[0]").value("xn--bcher-kva.de"));
    mvc.perform(get("/api/v1/users/me/analysis-excluded-domains").header("Authorization",
            "Bearer " + session.accessToken()).header("X-Device-Id", "999999"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_RELOGIN_REQUIRED"));
  }

  @Test
  void excludedDomainsCanBeReplacedClearedAndAreIsolated() throws Exception {
    LoginResponse first = desktop("domain-first");
    String path = "/api/v1/users/me/analysis-excluded-domains";
    String fifty = IntStream.range(0, 50).mapToObj(i -> "\"site" + i + ".example\"")
        .collect(Collectors.joining(",", "{\"domains\":[", "]}"));
    String fiftyOne = IntStream.range(0, 51).mapToObj(i -> "\"site" + i + ".example\"")
        .collect(Collectors.joining(",", "{\"domains\":[", "]}"));
    for (int i = 0; i < 2; i++) {
      mvc.perform(put(path).header("Authorization", "Bearer " + first.accessToken())
              .header("X-Device-Id", first.device().deviceId())
              .contentType(MediaType.APPLICATION_JSON).content(fifty))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.domains.length()").value(50));
    }
    mvc.perform(put(path).header("Authorization", "Bearer " + first.accessToken())
            .header("X-Device-Id", first.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content(fiftyOne))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path).header("Authorization", "Bearer " + first.accessToken())
            .header("X-Device-Id", first.device().deviceId()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.domains.length()").value(50));
    LoginResponse second = desktop("domain-second");
    mvc.perform(get(path).header("Authorization", "Bearer " + second.accessToken())
            .header("X-Device-Id", second.device().deviceId()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.domains.length()").value(0));
    mvc.perform(put(path).header("Authorization", "Bearer " + second.accessToken())
            .header("X-Device-Id", second.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content("{\"domains\":[\"second.example\"]}"))
        .andExpect(status().isOk());
    mvc.perform(get(path).header("Authorization", "Bearer " + first.accessToken())
            .header("X-Device-Id", first.device().deviceId()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.domains.length()").value(50));
    mvc.perform(put(path).header("Authorization", "Bearer " + first.accessToken())
            .header("X-Device-Id", first.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON).content("{\"domains\":[]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.domains.length()").value(0));
    mvc.perform(get(path).header("Authorization", "Bearer " + first.accessToken())
            .header("X-Device-Id", first.device().deviceId()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.domains.length()").value(0));
  }

  @Test
  void openApiDocumentsUserConfigurationEndpoints() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.summary")
            .value("현재 사용자 조회"))
        .andExpect(jsonPath("$.paths['/api/v1/users/me/focus-settings'].put"
            + ".responses['200'].description").value("저장 성공"))
        .andExpect(jsonPath("$.components.securitySchemes.focusOnBearer.scheme").value("bearer"))
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.security[0].focusOnBearer").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses['401'].content"
            + ".['application/json'].schema.['$ref']")
            .value("#/components/schemas/ApiErrorResponse"));
  }

  private LoginResponse desktop(String installationId) {
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), installationId,
        "http://127.0.0.1:49152", new DesktopDevice(installationId, "QA", "MACOS")));
  }
}
