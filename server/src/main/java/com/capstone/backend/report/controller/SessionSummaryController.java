package com.capstone.backend.report.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorResponse;
import com.capstone.backend.report.dto.SessionSummaryResponse;
import com.capstone.backend.report.service.SessionSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Desktop의 종료 결과 화면에 확정된 세션 정보를 제공한다. */
@RestController
@RequiredArgsConstructor
@Tag(name = "리포트")
@SecurityRequirement(name = "focusOnBearer")
public class SessionSummaryController {
  private final SessionSummaryService summaries;

  /** 실행 중인 세션은 상세 조회를 사용하고 결과 요약은 종료 뒤에 제공한다. */
  @GetMapping("/api/v1/sessions/{sessionId}/summary")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "학습 세션 결과 요약 조회",
      description = "Desktop 전용. ENDED/ABANDONED의 확정 시간과 실제 연장 이력을 반환합니다. "
          + "미집계 분석 시간·멈춤·개입 통계는 생략합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "종료 결과 조회 성공"),
      @ApiResponse(responseCode = "401",
          description = "인증 필요", content = @Content(
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "403",
          description = "Desktop 전용", content = @Content(
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404",
          description = "세션 없음 또는 소유권 없음", content = @Content(
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "409",
          description = "아직 종료되지 않은 세션", content = @Content(
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<SessionSummaryResponse> get(
      @AuthenticationPrincipal FocusOnPrincipal principal, @PathVariable String sessionId) {
    return com.capstone.backend.common.api.ApiResponse.of(summaries.get(principal, sessionId));
  }
}
