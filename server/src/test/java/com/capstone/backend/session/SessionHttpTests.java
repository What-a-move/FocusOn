package com.capstone.backend.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.persistence.UserEntity.OnboardingStatus;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.user.service.OnboardingReadinessService;
import com.capstone.backend.user.service.OnboardingReadinessService.Readiness;
import com.capstone.backend.user.service.OnboardingReadinessService.Steps;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/** 실제 루프백 HTTP 서버를 통해 Swagger와 세션 API를 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "test"})
class SessionHttpTests {
  @LocalServerPort private int port;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private OnboardingReadinessService onboarding;

  @Test
  void swaggerAndSessionRoundTrip() throws Exception {
    when(google.exchange(any())).thenReturn("session-http-" + UUID.randomUUID());
    when(onboarding.get(any(), isNull())).thenReturn(new Readiness(OnboardingStatus.COMPLETED,
        "COMPLETED", new Steps(true, true, true, true, true)));
    var owner = login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), "http-test",
        "http://127.0.0.1:49152", new DesktopDevice("http-test", "QA", "MACOS")));
    var client = HttpClient.newHttpClient();
    String root = "http://127.0.0.1:" + port;
    String token = "Bearer " + owner.accessToken();
    var goal = client.send(HttpRequest.newBuilder(URI.create(root + "/api/v1/goals"))
        .header("Authorization", token).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(
            "{\"goalText\":\"HTTP 목표\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(201, goal.statusCode());
    String goalId = json.readTree(goal.body()).path("data").path("goalId").asText();
    String body = "{\"goalId\":\"" + goalId + "\",\"targetDurationMs\":1800000,"
        + "\"timeZone\":\"Asia/Seoul\",\"sessionOptions\":{\"interventionLevel\":\"NORMAL\","
        + "\"notificationChannels\":[\"MOLE_UI\"],\"cameraAnalysisEnabled\":false},"
        + "\"startReadiness\":{\"screenAnalysisReady\":true}}";
    var started = client.send(HttpRequest.newBuilder(URI.create(root + "/api/v1/sessions"))
        .header("Authorization", token).header("Content-Type", "application/json")
        .header("X-Device-Id", owner.device().deviceId())
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(201, started.statusCode());
    String sessionId = json.readTree(started.body()).path("data").path("sessionId").asText();
    var active = client.send(HttpRequest.newBuilder(URI.create(root + "/api/v1/sessions/active"))
        .header("Authorization", token).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, active.statusCode());
    assertEquals(sessionId, json.readTree(active.body()).path("data").path("sessionId").asText());
    long version = json.readTree(started.body()).path("data").path("version").asLong();
    var ended = client.send(HttpRequest.newBuilder(URI.create(root + "/api/v1/sessions/"
        + sessionId + "/end")).header("Authorization", token)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .header("If-Match-Version", Long.toString(version))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"accumulatedActiveMs\":1000}"))
        .build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, ended.statusCode());
    assertEquals("ENDED", json.readTree(ended.body()).path("data").path("status").asText());
    assertEquals(1000, json.readTree(ended.body()).path("data")
        .path("accumulatedActiveMs").asLong());
    var noActive = client.send(HttpRequest.newBuilder(URI.create(root + "/api/v1/sessions/active"))
        .header("Authorization", token).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(204, noActive.statusCode());
    var spec = client.send(HttpRequest.newBuilder(URI.create(root + "/v3/api-docs"))
        .GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, spec.statusCode());
    assertTrue(json.readTree(spec.body()).path("paths").has("/api/v1/sessions"));
    assertTrue(json.readTree(spec.body()).path("paths").has("/api/v1/sessions/active"));
    assertTrue(json.readTree(spec.body()).path("paths")
        .has("/api/v1/sessions/{sessionId}/end"));
    var swagger = client.send(HttpRequest.newBuilder(URI.create(root + "/swagger-ui/index.html"))
        .GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, swagger.statusCode());
  }
}
