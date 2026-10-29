package com.capstone.backend.session.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorResponse;
import com.capstone.backend.session.dto.SessionDtos.ActiveResponse;
import com.capstone.backend.session.dto.SessionDtos.StartRequest;
import com.capstone.backend.session.dto.SessionDtos.StartResponse;
import com.capstone.backend.session.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 학습 세션 시작과 활성 세션 조회 API다. */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "학습 세션")
@SecurityRequirement(name = "focusOnBearer")
public class SessionController {
  private final SessionService sessions;

  /** Desktop에서 세션을 시작한다. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "학습 세션 시작")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "세션 시작 성공"),
      @ApiResponse(responseCode = "400", description = "요청 값 또는 헤더 오류",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증 필요",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "목표를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "409", description = "시작 조건 또는 멱등 키 충돌",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<StartResponse> start(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @Parameter(description = "인증된 Desktop 기기 ID", required = true)
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
      @Parameter(description = "요청 재시도를 위한 UUID", required = true)
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody StartRequest request) {
    return com.capstone.backend.common.api.ApiResponse.of(
        sessions.start(principal, deviceId, key, request));
  }

  /** 현재 사용자의 활성 세션이 없으면 본문 없이 204를 반환한다. */
  @GetMapping("/active")
  @Operation(summary = "활성 학습 세션 조회")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "활성 세션 조회 성공"),
      @ApiResponse(responseCode = "204", description = "활성 세션 없음"),
      @ApiResponse(responseCode = "401", description = "인증 필요",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public ResponseEntity<com.capstone.backend.common.api.ApiResponse<ActiveResponse>> active(
      @AuthenticationPrincipal FocusOnPrincipal principal) {
    return sessions.active(principal)
        .map(value -> ResponseEntity.ok(com.capstone.backend.common.api.ApiResponse.of(value)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }
}
