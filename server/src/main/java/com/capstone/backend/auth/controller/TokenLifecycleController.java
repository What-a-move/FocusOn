package com.capstone.backend.auth.controller;

import com.capstone.backend.auth.dto.TokenLifecycleDtos.RefreshRequest;
import com.capstone.backend.auth.dto.TokenLifecycleDtos.TokenPair;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.service.TokenLifecycleService;
import com.capstone.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Google 교환 API와 별개로 FocusOn token 수명주기를 제공한다. */
@RestController
@RequestMapping("/api/v1/auth")
public class TokenLifecycleController {
  private final TokenLifecycleService lifecycle;

  /** 요청 바인딩 전 필터에서 한도를 확인한 뒤 인증 수명주기 사용 사례를 호출한다. */
  public TokenLifecycleController(TokenLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  /** Bearer가 아닌 body의 Refresh Token을 인증 수단으로 사용한다. */
  @PostMapping("/refresh")
  @Operation(summary = "Refresh Token 회전",
      description = "Bearer 불필요. 성공 후 받은 새 Refresh Token으로 교체하며 이전 값은 재사용하지 않습니다.")
  @SecurityRequirements
  public ApiResponse<TokenPair> refresh(@Valid @RequestBody(required = false) RefreshRequest body) {
    return ApiResponse.of(lifecycle.refresh(body == null ? null : body.refreshToken()));
  }

  /** 대상 user/device/family를 요청 body로 받지 않고 검증된 principal로 확정한다. */
  @PostMapping("/logout")
  @Operation(summary = "현재 Desktop 및 연결 Extension 로그아웃",
      description = "현재 Access Token으로 요청합니다. 성공 응답은 204이며 본문이 없습니다.")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@AuthenticationPrincipal FocusOnPrincipal principal) {
    lifecycle.logout(principal);
  }
}
