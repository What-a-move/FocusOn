package com.capstone.backend.common.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공통 응답 모델과 예외 처리기가 실제 HTTP 응답으로 연결되는지 확인한다. */
class ApiExceptionHandlerTests {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
        .setControllerAdvice(new ApiExceptionHandler())
        .addFilters(new RequestIdFilter())
        .build();
  }

  @Test
  void successUsesDataWrapper() throws Exception {
    mvc.perform(get("/api/v1/probe"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ok").value(true));
  }

  @Test
  void validationAndMalformedJsonUsePublicErrorShape() throws Exception {
    mvc.perform(post("/api/v1/probe/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"value\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.retryable").value(false))
        .andExpect(jsonPath("$.retryAfterSeconds").value((Object) null))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.details").isMap());

    mvc.perform(post("/api/v1/probe/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void rateLimitUsesMatchingHeaderAndBody() throws Exception {
    mvc.perform(get("/api/v1/probe/rate"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "7"))
        .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
        .andExpect(jsonPath("$.retryable").value(true))
        .andExpect(jsonPath("$.retryAfterSeconds").value(7));
  }

  @Test
  void unexpectedFailureDoesNotExposeExceptionMessage() throws Exception {
    mvc.perform(get("/api/v1/probe/failure").header("X-Request-Id", "client-supplied"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value(ApiErrorCode.INTERNAL_ERROR.defaultMessage()))
        .andExpect(jsonPath("$.requestId").value(startsWith("req_")))
        .andExpect(jsonPath("$.requestId").value(not("client-supplied")))
        .andExpect(content().string(not(containsString("internal-secret"))));
  }

  @Test
  void unsupportedMethodDoesNotBecomeServerError() throws Exception {
    mvc.perform(post("/api/v1/probe"))
        .andExpect(status().isMethodNotAllowed());
  }

  @RestController
  @RequestMapping("/api/v1/probe")
  static class ProbeController {
    @GetMapping
    ApiResponse<Map<String, Boolean>> success() {
      return ApiResponse.of(Map.of("ok", true));
    }

    @PostMapping("/validate")
    ApiResponse<String> validate(@Valid @RequestBody ProbeInput input) {
      return ApiResponse.of(input.value());
    }

    @GetMapping("/rate")
    void rate() {
      throw ApiException.tooManyRequests(7);
    }

    @GetMapping("/failure")
    void failure() {
      throw new IllegalStateException("internal-secret");
    }
  }

  record ProbeInput(@NotBlank String value) {}
}
