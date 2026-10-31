package com.capstone.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.security.RequestAuthenticationService;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

/** 내장 서버에서 실제 오류 디스패치와 인증 경계를 확인한다. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MissingEndpointHttpTests {
  @LocalServerPort private int port;
  @MockitoBean private RequestAuthenticationService authentication;
  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void authenticatedMissingEndpointIsNotRelogin() throws Exception {
    when(authentication.authenticate("valid"))
        .thenReturn(new FocusOnPrincipal(1, 2, 3, DeviceType.DESKTOP));
    var response = send("/api/v1/reports/calendar?month=2026-10", "Bearer valid");
    assertEquals(404, response.statusCode());
    assertFalse(response.body().contains("AUTH_RELOGIN_REQUIRED"));
    var body = JsonMapper.builder().build().readTree(response.body());
    assertEquals("NOT_FOUND", body.path("code").asString());
    org.junit.jupiter.api.Assertions.assertTrue(
        body.path("requestId").asString().startsWith("req_"));
    assertFalse(body.has("path"));
  }

  @Test
  void authenticationRemainsRequiredAndErrorIsNotPublic() throws Exception {
    when(authentication.authenticate(anyString()))
        .thenThrow(new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    assertEquals(401, send("/api/v1/reports/calendar", null).statusCode());
    assertEquals(401, send("/api/v1/reports/calendar", "Bearer forged").statusCode());
    assertEquals(401, send("/error", null).statusCode());
  }

  @Test
  void expiredTokenKeepsItsSpecificAuthenticationError() throws Exception {
    when(authentication.authenticate("expired"))
        .thenThrow(new ApiException(ApiErrorCode.AUTH_ACCESS_TOKEN_EXPIRED));
    var response = send("/api/v1/reports/calendar", "Bearer expired");
    assertEquals(401, response.statusCode());
    org.junit.jupiter.api.Assertions.assertTrue(
        response.body().contains("AUTH_ACCESS_TOKEN_EXPIRED"));
  }

  private HttpResponse<String> send(String path, String authorization) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
    if (authorization != null) {
      request.header("Authorization", authorization);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
