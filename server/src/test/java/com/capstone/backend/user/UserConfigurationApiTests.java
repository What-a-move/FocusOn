package com.capstone.backend.user;

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

/** 인증된 사용자 설정 API의 소유권과 도메인 정규화를 HTTP 경로로 검증한다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class UserConfigurationApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @MockitoBean private GoogleOauthClient google;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    when(google.exchange(any())).thenReturn("user-config-" + UUID.randomUUID());
  }

  @Test
  void profileAndFocusSettingsUseOnlyAuthenticatedUser() throws Exception {
    LoginResponse session = desktop("profile-device");
    mvc.perform(put("/api/v1/users/me/profile").header("Authorization",
            "Bearer " + session.accessToken()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nickname\":\"focuson\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("focuson"));
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
  void excludedDomainsRequireMatchingDeviceAndNormalizeHosts() throws Exception {
    LoginResponse session = desktop("domain-device");
    mvc.perform(put("/api/v1/users/me/analysis-excluded-domains").header("Authorization",
            "Bearer " + session.accessToken()).header("X-Device-Id", session.device().deviceId())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"domains\":[\"YouTube.COM.\",\"xn--bcher-kva.de\",\"youtube.com\"]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.domains.length()").value(2))
        .andExpect(jsonPath("$.data.domains[0]").value("xn--bcher-kva.de"));
    mvc.perform(get("/api/v1/users/me/analysis-excluded-domains").header("Authorization",
            "Bearer " + session.accessToken()).header("X-Device-Id", "999999"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_RELOGIN_REQUIRED"));
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
