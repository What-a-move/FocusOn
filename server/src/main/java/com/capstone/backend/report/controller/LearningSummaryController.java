package com.capstone.backend.report.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorResponse;
import com.capstone.backend.report.dto.LearningSummaryResponse;
import com.capstone.backend.report.service.LearningSummaryService;
import com.capstone.backend.report.service.LearningSummaryService.SummaryListItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/** Desktop 학습 노트에 실제 정리 상태를 제공한다. */
@RestController
@RequiredArgsConstructor
@Tag(name = "리포트")
@SecurityRequirement(name = "focusOnBearer")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "인증·기기·연결 오류",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "403", description = "호출 권한 없음",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "404", description = "정리 없음 또는 세션 소유권 없음",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "503", description = "과거 저장 결과가 유효하지 않아 제공 불가",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class LearningSummaryController {
  private final LearningSummaryService summaries;

  /** 본인 세션의 완료된 정리 목록을 반환한다. */
  @GetMapping("/api/v1/learning-summaries")
  @Operation(summary = "완료된 학습 정리 목록 조회")
  public com.capstone.backend.common.api.ApiResponse<List<SummaryListItem>> list(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @RequestParam(defaultValue = "0") int page) {
    return com.capstone.backend.common.api.ApiResponse.of(summaries.list(principal, page));
  }

  /** 생성 중·완료·실패를 저장된 상태 그대로 제공한다. */
  @GetMapping("/api/v1/sessions/{sessionId}/learning-summary")
  @ResponseStatus(HttpStatus.OK)
  @ApiResponse(responseCode = "200", description = "실제 학습 정리 상태 조회 성공")
  @Operation(summary = "세션 학습 정리 조회",
      description = "유효한 저장 상태는200, 정리 행이 없으면404, 불량 READY 결과는503입니다. "
          + "조회로 정리 작업을 생성하거나 저장 결과를 변경하지 않습니다.")
  public com.capstone.backend.common.api.ApiResponse<LearningSummaryResponse> get(
      @AuthenticationPrincipal FocusOnPrincipal principal, @PathVariable String sessionId) {
    return com.capstone.backend.common.api.ApiResponse.of(summaries.get(principal, sessionId));
  }
}
