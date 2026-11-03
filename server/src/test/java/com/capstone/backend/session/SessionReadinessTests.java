package com.capstone.backend.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 모의하지 않은 온보딩 서비스로 준비되지 않은 사용자 요청을 차단한다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class SessionReadinessTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @MockitoBean private GoogleOauthClient google;

  @Test
  void unlinkedAndUnconsentedDesktopCannotStart() throws Exception {
    when(google.exchange(any())).thenReturn("unready-" + UUID.randomUUID());
    var owner = login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), "unready",
        "http://127.0.0.1:49152", new DesktopDevice("unready", "QA", "MACOS")));
    var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    String body = "{\"goalId\":\"1\",\"targetDurationMs\":1800000,"
        + "\"sessionOptions\":{\"interventionLevel\":\"NORMAL\","
        + "\"notificationChannels\":[\"MOLE_UI\"],\"cameraAnalysisEnabled\":false},"
        + "\"startReadiness\":{\"screenAnalysisReady\":true}}";
    mvc.perform(post("/api/v1/sessions")
            .header("Authorization", "Bearer " + owner.accessToken())
            .header("X-Device-Id", owner.device().deviceId())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_START_REQUIREMENT_MISSING"));
  }
}
