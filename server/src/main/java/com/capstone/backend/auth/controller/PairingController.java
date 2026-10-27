package com.capstone.backend.auth.controller;

import com.capstone.backend.auth.dto.PairingDtos.ClaimRequest;
import com.capstone.backend.auth.dto.PairingDtos.IssuedCode;
import com.capstone.backend.auth.dto.PairingDtos.LinkedExtension;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.service.PairingService;
import com.capstone.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Desktop 코드 발급·취소와 공개 Extension 최초 연결을 제공한다. */
@RestController
@RequestMapping("/api/v1")
public class PairingController {
  private final PairingService pairing;

  /** 입력 변환 뒤 서비스에 소유권·상태 전이를 맡긴다. */
  public PairingController(PairingService pairing) {
    this.pairing = pairing;
  }

  /** 온보딩 미완료 상태도 현재 활성 Desktop이면 발급할 수 있다. */
  @PostMapping("/pairing-codes")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Extension 연결 코드 발급")
  public ApiResponse<IssuedCode> issue(@AuthenticationPrincipal FocusOnPrincipal principal) {
    return ApiResponse.of(pairing.issue(principal));
  }

  /** 이미 연결된 Extension token과 무관하게 일회용 코드로만 연결한다. */
  @PostMapping("/extension-pairings")
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirements
  @Operation(summary = "연결 코드로 Extension 인증", description = "Bearer 불필요. 코드 원문은 로그에 남기지 않습니다.")
  public ApiResponse<LinkedExtension> claim(@Valid @RequestBody ClaimRequest body,
      HttpServletRequest request) {
    return ApiResponse.of(pairing.claim(body, request.getRemoteAddr()));
  }

  /** 소비 완료 후 취소 요청이 토큰을 폐기하지 않으며 없는 ID도 204로 처리한다. */
  @DeleteMapping("/pairing-codes/{pairingCodeId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "현재 Desktop의 연결 코드 취소")
  public void cancel(@AuthenticationPrincipal FocusOnPrincipal principal,
      @PathVariable long pairingCodeId) {
    pairing.cancel(principal, pairingCodeId);
  }
}
