package com.capstone.backend.session.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorResponse;
import com.capstone.backend.session.service.SessionQueryService;
import com.capstone.backend.session.service.SessionQueryService.Detail;
import com.capstone.backend.session.service.SessionQueryService.History;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증된 사용자의 세션 스냅샷과 기록 목록을 제공한다. */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "학습 세션")
@SecurityRequirement(name = "focusOnBearer")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "인증 필요",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "400", description = "조회 조건 오류",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class SessionQueryController {
  private final SessionQueryService queries;

  /** 타인 세션과 없는 세션은 같은 404로 처리한다. */
  @GetMapping("/{sessionId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "본인 학습 세션 상세 조회")
  @ApiResponse(responseCode = "404", description = "세션 없음 또는 소유권 없음",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  public com.capstone.backend.common.api.ApiResponse<Detail> detail(
      @AuthenticationPrincipal FocusOnPrincipal principal, @PathVariable String sessionId) {
    return com.capstone.backend.common.api.ApiResponse.of(queries.detail(principal, sessionId));
  }

  /** 시작 시 고정한 학습 날짜의 양 끝을 포함하고 ID 내림차순으로 조회한다. */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "본인 학습 세션 목록 조회",
      description = "from/to는 YYYY-MM-DD. limit 기본 20, 최대 100. nextCursor로 다음 페이지를 조회합니다. "
          + "timeZone은 유효성만 검사하며 저장된 학습 날짜를 재배정하지 않습니다.")
  @ApiResponse(responseCode = "403", description = "Desktop 전용 조회",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  public com.capstone.backend.common.api.ApiResponse<History> history(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @RequestParam String from, @RequestParam String to,
      @RequestParam(required = false) String timeZone,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return com.capstone.backend.common.api.ApiResponse.of(
        queries.history(principal, from, to, timeZone, cursor, limit));
  }
}
