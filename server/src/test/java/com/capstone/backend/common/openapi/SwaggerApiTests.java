package com.capstone.backend.common.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.service.GoogleOauthClient;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 개발 문서 공개와 제품 API 인증의 경계를 검증한다. Google 성공은 합성 응답이다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class SwaggerApiTests {
  @Autowired private WebApplicationContext context;
  @MockitoBean private GoogleOauthClient google;
  private MockMvc mvc;
  private final JsonMapper json = JsonMapper.builder().build();

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
  }

  @Test
  void documentDeclaresBearerAndPublicOperationsWithoutPrincipalParameter() throws Exception {
    JsonNode doc = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
    assertEquals("bearer", doc.at("/components/securitySchemes/focusOnBearer/scheme").asText());
    assertTrue(doc.at("/security/0").has("focusOnBearer"));
    assertEquals(0, doc.at("/paths/~1api~1v1~1auth~1google~1exchange/post/security").size());
    assertTrue(doc.at("/paths/~1api~1v1~1auth~1google~1exchange/post/security").isArray());
    assertTrue(doc.at("/paths/~1api~1v1~1auth~1refresh/post/security").isArray());
    assertEquals(0, doc.at("/paths/~1api~1v1~1auth~1refresh/post/security").size());
    assertFalse(doc.at("/paths/~1api~1v1~1auth~1me/get").has("parameters"));
    assertFalse(doc.at("/paths/~1api~1v1~1auth~1logout/post").has("parameters"));
    assertTrue(doc.path("paths").has("/api/v1/users/me"));
    assertTrue(doc.path("paths").has("/api/v1/users/me/analysis-excluded-domains"));
    assertFalse(doc.path("paths").has("/actuator/health"));
    assertEquals("google-authorization-code-from-pkce-tool",
        doc.at("/components/schemas/GoogleExchangeRequest/properties/authorizationCode/example")
            .asText());
    assertEquals("2026-09-17", doc.at(
        "/components/schemas/UpdateConsentRequest/properties/consentVersion/example").asText());
    assertEquals("youtube.com", doc.at(
        "/components/schemas/ReplaceExcludedDomainsRequest/properties/domains/items/example")
            .asText());
    assertEquals("MOLE_UI", doc.at(
        "/components/schemas/UpdateFocusSettingsRequest/properties/notificationChannels"
            + "/items/example").asText());
  }

  @Test
  void servesUiConfigurationButCannotBypassProtectedApi() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
        .andExpect(jsonPath("$.persistAuthorization").value(false));
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/google/exchange").contentType(MediaType.APPLICATION_JSON)
        .content("{}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
        .content("{}")).andExpect(status().isUnauthorized());
    mvc.perform(post("/v3/api-docs")).andExpect(status().isForbidden());
  }

  @Test
  void exchangeMeRefreshLogoutFlowStillUsesRealAuthenticationChain() throws Exception {
    String installation = "swagger-" + UUID.randomUUID();
    when(google.exchange(any())).thenReturn("swagger-sub-" + UUID.randomUUID());
    String request = """
        {"authorizationCode":"synthetic-code","codeVerifier":"%s","state":"synthetic-state",
         "redirectUri":"http://127.0.0.1:49152/","device":{"installationId":"%s",
         "deviceName":"Swagger QA","platform":"MACOS"}}
        """.formatted("a".repeat(43), installation);
    JsonNode login = json.readTree(mvc.perform(post("/api/v1/auth/google/exchange")
        .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString()).path("data");
    mvc.perform(get("/api/v1/auth/me")
        .header("Authorization", "Bearer " + login.path("accessToken").asText()))
        .andExpect(status().isOk());
    String refresh = json.createObjectNode().put("refreshToken",
        login.path("refreshToken").asText()).toString();
    JsonNode rotated = json.readTree(mvc.perform(post("/api/v1/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON).content(refresh)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString()).path("data");
    String bearer = "Bearer " + rotated.path("accessToken").asText();
    mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer))
        .andExpect(status().isUnauthorized());
  }
}
