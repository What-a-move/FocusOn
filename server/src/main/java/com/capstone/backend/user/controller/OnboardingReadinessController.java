package com.capstone.backend.user.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiResponse;
import com.capstone.backend.user.service.OnboardingReadinessService;
import com.capstone.backend.user.service.OnboardingReadinessService.Readiness;
import com.capstone.backend.user.service.OnboardingReadinessService.Update;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Desktop 온보딩 재개 상태를 조회하고 클라이언트 확인 항목을 저장한다. */
@RestController
@RequestMapping("/api/v1/users/me/onboarding-readiness")
public class OnboardingReadinessController {
  private final OnboardingReadinessService readiness;

  /** 준비 상태 서비스에 조회와 변경을 위임한다. */
  public OnboardingReadinessController(OnboardingReadinessService readiness) {
    this.readiness = readiness;
  }

  /** 단계별 서버 상태를 조회한다. */
  @GetMapping
  @Operation(summary = "온보딩 준비 상태 조회")
  public ApiResponse<Readiness> get(@AuthenticationPrincipal FocusOnPrincipal principal) {
    return ApiResponse.of(readiness.get(principal, null));
  }

  /** Desktop에서 확인한 화면 권한과 제외 단계 완료를 부분 갱신한다. */
  @PutMapping
  @Operation(summary = "온보딩 준비 상태 저장")
  public ApiResponse<Readiness> put(@AuthenticationPrincipal FocusOnPrincipal principal,
      @RequestBody Update update) {
    return ApiResponse.of(readiness.get(principal, update));
  }
}
