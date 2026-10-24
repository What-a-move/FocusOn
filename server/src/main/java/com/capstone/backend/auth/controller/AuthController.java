package com.capstone.backend.auth.controller;

import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.Identity;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.service.AuthRateLimiter;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.auth.service.RedirectUriValidator;
import com.capstone.backend.auth.service.TokenSecrets;
import com.capstone.backend.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Desktop의 Google 교환과 인증 사용자 조회 API다. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final LoginService login;
  private final AuthRateLimiter limits;

  /** 공통 한도와 로그인 사용 사례를 연결한다. */
  public AuthController(LoginService login, AuthRateLimiter limits) {
    this.login = login;
    this.limits = limits;
  }

  /** state 검증은 Desktop이 수행하며 서버는 code·PKCE·redirect URI를 검증한다. */
  @PostMapping("/google/exchange")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<LoginResponse> exchange(@Valid @RequestBody GoogleExchangeRequest body,
      HttpServletRequest request) {
    RedirectUriValidator.validate(body.redirectUri());
    limits.check("google:" + TokenSecrets.hash(request.getRemoteAddr() + "\n"
        + body.device().installationId()), 5, Duration.ofMinutes(10));
    return ApiResponse.of(login.exchange(body));
  }

  /** userId query 없이 검증된 요청 주체의 정보만 반환한다. */
  @GetMapping("/me")
  public ApiResponse<Identity> me(@AuthenticationPrincipal FocusOnPrincipal principal) {
    return ApiResponse.of(login.me(principal));
  }
}
