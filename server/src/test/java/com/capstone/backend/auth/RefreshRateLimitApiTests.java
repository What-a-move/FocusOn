package com.capstone.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.capstone.backend.common.api.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Security chain과 MVC 바인딩을 함께 지나 IP 한도의 적용 위치를 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
class RefreshRateLimitApiTests {
  @Autowired private WebApplicationContext context;
  private MockMvc mvc;

  /** 테스트마다 서로 다른 IP 키를 써 공유 limiter의 이전 사용량과 분리한다. */
  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
  }

  /** 깨진 JSON도 300회 예산을 사용하고 301번째는 파싱 전에 차단한다. */
  @Test
  void malformedJsonConsumesIpQuotaBeforeBinding() throws Exception {
    exhaust("{", 400);
  }

  /** 길이 검증 실패도 공개 refresh IP 한도에 포함한다. */
  @Test
  void oversizedTokenConsumesIpQuotaBeforeValidation() throws Exception {
    exhaust("{\"refreshToken\":\"" + "a".repeat(4097) + "\"}", 400);
  }

  /** 정상 형식은 두 번 집계하지 않으며 다른 IP의 예산을 소진하지 않는다. */
  @Test
  void validRequestsCountOnceAndDoNotConsumeAnotherIpQuota() throws Exception {
    exhaust("{\"refreshToken\":\"synthetic-unknown\"}", 401);
  }

  /** 동일 IP의 300회 응답과 301번째 공통 오류·재시도 시각을 직접 비교한다. */
  private void exhaust(String body, int expectedStatus) throws Exception {
    String ip = "qa-refresh-" + UUID.randomUUID();
    for (int i = 0; i < 300; i++) {
      assertEquals(expectedStatus, send(ip, body).getStatus(), "request " + (i + 1));
    }
    var blocked = send(ip, body);
    // 수정 전/후 실행 로그에 값만 기록한다. 요청 본문이나 token은 출력하지 않는다.
    System.out.println("REFRESH_QUOTA expectedInputStatus=" + expectedStatus
        + " first300=" + expectedStatus + " request301=" + blocked.getStatus());
    assertEquals(429, blocked.getStatus());
    assertTrue(Integer.parseInt(blocked.getHeader("Retry-After")) > 0);
    var error = JsonMapper.builder().build().readTree(blocked.getContentAsString());
    assertEquals("TOO_MANY_REQUESTS", error.path("code").asString());
    assertTrue(!error.path("requestId").asString().isBlank());
    assertEquals(401, send(ip + "-other", "{}").getStatus());
  }

  /** 외부 forwarded header 대신 실제 remoteAddr를 주입한다. */
  private org.springframework.mock.web.MockHttpServletResponse send(String ip, String body)
      throws Exception {
    return mvc.perform(post("/api/v1/auth/refresh").with(request -> {
      request.setRemoteAddr(ip);
      return request;
    }).contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn().getResponse();
  }
}
