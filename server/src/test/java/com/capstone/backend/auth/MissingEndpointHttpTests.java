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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

/** 내장 서버에서 실제 오류 디스패치와 인증 경계를 확인한다. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
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
  void unsupportedMethodUsesCommonErrorBody() throws Exception {
    when(authentication.authenticate("valid"))
        .thenReturn(new FocusOnPrincipal(1, 2, 3, DeviceType.DESKTOP));
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
        + "/api/v1/auth/me")).header("Authorization", "Bearer valid")
        .POST(HttpRequest.BodyPublishers.noBody()).build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(405, response.statusCode());
    var body = JsonMapper.builder().build().readTree(response.body());
    assertEquals("METHOD_NOT_ALLOWED", body.path("code").asString());
    org.junit.jupiter.api.Assertions.assertTrue(
        body.path("requestId").asString().startsWith("req_"));
    assertFalse(body.has("path"));
  }

  @Test
  void jsonLogsTraceAndMasksUnknownPathAndQuery(CapturedOutput output) throws Exception {
    when(authentication.authenticate("valid"))
        .thenReturn(new FocusOnPrincipal(1, 2, 3, DeviceType.DESKTOP));
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
        + "/api/v1/private-path-secret?secret=private-query-secret"))
        .header("Authorization", "Bearer valid").header("X-Trace-Id", "trace-test-12345")
        .GET().build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(404, response.statusCode());
    assertEquals("trace-test-12345", response.headers().firstValue("X-Trace-Id").orElseThrow());
    var lines = output.getOut().lines().filter(line -> line.contains("trace-test-12345")).toList();
    assertEquals(2, lines.size());
    var json = JsonMapper.builder().build();
    var input = json.readTree(lines.getFirst());
    final var result = json.readTree(lines.getLast());
    assertEquals("http.in", input.path("event").asString());
    assertFalse(input.path("ts").asString().isBlank());
    assertEquals("backend", input.path("module").asString());
    assertEquals("request-filter", input.path("component").asString());
    assertEquals(0, input.path("durMs").asInt());
    assertEquals("/api/v1/{redacted}", input.path("path").asString());
    assertEquals("http.out", result.path("event").asString());
    assertEquals(404, result.path("status").asInt());
    assertEquals(input.path("requestId").asString(), result.path("requestId").asString());
    assertFalse(output.getOut().contains("private-path-secret"));
    assertFalse(output.getOut().contains("private-query-secret"));
    assertFalse(output.getOut().contains("Bearer valid"));
  }

  @Test
  void unauthorizedLogIncludesSafeStageAndCode(CapturedOutput output) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
        + "/api/v1/reports/calendar")).header("X-Trace-Id", "trace-auth-12345").GET().build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(401, response.statusCode());
    var outputLine = output.getOut().lines().filter(line -> line.contains("trace-auth-12345"))
        .filter(line -> line.contains("http.out")).findFirst().orElseThrow();
    var json = JsonMapper.builder().build().readTree(outputLine);
    assertEquals("security", json.path("stage").asString());
    assertEquals("AUTH_RELOGIN_REQUIRED", json.path("code").asString());
    assertEquals("auth", json.path("errorClass").asString());
    assertEquals(401, json.path("status").asInt());
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
