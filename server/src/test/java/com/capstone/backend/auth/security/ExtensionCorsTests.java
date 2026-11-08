package com.capstone.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 브라우저 CORS와 제품 API 인증 경계를 함께 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "focuson.cors.allowed-origins="
    + "chrome-extension://fbhlmioghblalhmcokcpoapjpnjccbde")
@ActiveProfiles("test")
class ExtensionCorsTests {
  private static final String ORIGIN = "chrome-extension://fbhlmioghblalhmcokcpoapjpnjccbde";
  @Autowired private WebApplicationContext context;
  private MockMvc mvc;
  @org.springframework.boot.test.web.server.LocalServerPort private int port;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void validPreflightDoesNotRequireTokenAndAllowsContractHeaders() throws Exception {
    mvc.perform(options("/api/v1/sessions/active").header("Origin", ORIGIN)
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization,Content-Type,Idempotency-Key,"
                + "If-Match-Version,X-Device-Id,X-Trace-Id"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }

  /** 연결과 토큰 갱신의 POST 허용이 빠져도 회귀를 검출한다. */
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "/api/v1/extension-pairings", "/api/v1/auth/refresh"
  })
  void pairingAndRefreshPostPreflightsAllowRequiredHeaders(String path) throws Exception {
    mvc.perform(options(path).header("Origin", ORIGIN)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Content-Type,X-Device-Id,X-Trace-Id"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
        .andExpect(header().string("Access-Control-Allow-Methods",
            org.hamcrest.Matchers.containsString("POST")))
        .andExpect(header().string("Access-Control-Allow-Headers",
            org.hamcrest.Matchers.containsString("Content-Type")))
        .andExpect(header().string("Access-Control-Allow-Headers",
            org.hamcrest.Matchers.containsString("X-Device-Id")))
        .andExpect(header().string("Access-Control-Allow-Headers",
            org.hamcrest.Matchers.containsString("X-Trace-Id")));
  }

  @Test
  void actualRequestStillRequiresAuthenticationAndExposesRetryAfter() throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("Origin", ORIGIN))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
        .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
    mvc.perform(options("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized());
  }

  /** 허용되지 않은 출처·메서드·헤더를 브라우저에 열지 않는지 확인한다. */
  @Test
  void unrelatedOriginsMethodsAndHeadersAreRejected() throws Exception {
    for (String origin : new String[] {"https://example.com", "null",
        "chrome-extension://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}) {
      mvc.perform(options("/api/v1/sessions/active").header("Origin", origin)
              .header("Access-Control-Request-Method", "GET"))
          .andExpect(status().isForbidden())
          .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    mvc.perform(options("/api/v1/auth/me").header("Origin", ORIGIN)
            .header("Access-Control-Request-Method", "PATCH"))
        .andExpect(status().isForbidden());
    mvc.perform(options("/api/v1/auth/me").header("Origin", ORIGIN)
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "X-Untrusted"))
        .andExpect(status().isForbidden());
  }

  @Test
  void realHttpPreflightAndAuthenticationBoundary() throws Exception {
    var client = java.net.http.HttpClient.newHttpClient();
    var uri = java.net.URI.create("http://127.0.0.1:" + port + "/api/v1/sessions/active");
    var preflight = client.send(java.net.http.HttpRequest.newBuilder(uri)
        .header("Origin", ORIGIN).header("Access-Control-Request-Method", "GET")
        .header("Access-Control-Request-Headers", "authorization")
        .method("OPTIONS", java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
    org.junit.jupiter.api.Assertions.assertEquals(200, preflight.statusCode());
    org.junit.jupiter.api.Assertions.assertEquals(ORIGIN,
        preflight.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    var actual = client.send(java.net.http.HttpRequest.newBuilder(uri)
        .header("Origin", ORIGIN).GET().build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
    org.junit.jupiter.api.Assertions.assertEquals(401, actual.statusCode());
  }

  /** 잘못된 환경변수가 전체 origin 허용으로 바뀌지 않게 한다. */
  @Test
  void invalidConfigurationCannotEnableWildcardOrWebOrigin() {
    var config = new ExtensionCorsConfiguration();
    for (String origin : new String[] {"*", "null", "https://example.com",
        ORIGIN + "/", ORIGIN + ",*"}) {
      assertThrows(IllegalArgumentException.class, () -> config.corsConfigurationSource(origin));
    }
  }
}
