package com.capstone.backend.goal.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorResponse;
import com.capstone.backend.goal.dto.GoalDtos.CreateGoalRequest;
import com.capstone.backend.goal.dto.GoalDtos.GoalResponse;
import com.capstone.backend.goal.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 학습 목표 생성과 조회 API다. */
@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
@Tag(name = "학습 목표")
@SecurityRequirement(name = "focusOnBearer")
public class GoalController {
  private final GoalService goals;

  /** 수동으로 확정한 목표를 저장한다. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "학습 목표 저장",
      description = "AI 확인 대화 연동 전에는 수동 확정 목표만 저장하며, "
          + "수동 확정에 포함된 대화 ID는 검증하거나 저장하지 않습니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "목표 저장 성공"),
      @ApiResponse(responseCode = "400", description = "입력값 오류",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증 필요",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "503", description = "AI 확인 대화 연동 대기",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<GoalResponse> create(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @Valid @RequestBody CreateGoalRequest request) {
    return com.capstone.backend.common.api.ApiResponse.of(goals.create(principal, request));
  }

  /** 본인 소유의 목표를 조회한다. */
  @GetMapping("/{goalId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "학습 목표 상세 조회")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "목표 조회 성공"),
      @ApiResponse(responseCode = "401", description = "인증 필요",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "목표를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<GoalResponse> get(
      @AuthenticationPrincipal FocusOnPrincipal principal, @PathVariable String goalId) {
    return com.capstone.backend.common.api.ApiResponse.of(goals.get(principal, goalId));
  }
}
