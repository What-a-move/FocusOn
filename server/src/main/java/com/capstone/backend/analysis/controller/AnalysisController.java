package com.capstone.backend.analysis.controller;

import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.dto.AnalysisDtos.Response;
import com.capstone.backend.analysis.service.AnalysisService;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 분석 입력은 요청 처리 중에만 사용하고 결과만 반환한다. */
@RestController
@RequiredArgsConstructor
@Tag(name = "화면 분석")
@SecurityRequirement(name = "focusOnBearer")
public class AnalysisController {
  private final AnalysisService analyses;

  /** Desktop에서 본인 세션에 저장된 분석 결과를 조회한다. */
  @GetMapping("/api/v1/sessions/{sessionId}/analysis-runs")
  @Operation(summary = "세션 분석 이력 조회", description = "관측 시각 오름차순으로 조회합니다.")
  public ApiResponse<List<Response>> history(@AuthenticationPrincipal FocusOnPrincipal principal,
      @PathVariable String sessionId) {
    return ApiResponse.of(analyses.history(principal, sessionId));
  }

  /** 현재 실행 세션의 Desktop 또는 연결 Extension에서 분석을 제출한다. */
  @PostMapping("/api/v1/sessions/{sessionId}/analysis-runs")
  @Operation(summary = "화면 관련성 분석", description = "원문을 저장하지 않습니다. "
      + "AI 장애도 200 UNCERTAIN/ANALYSIS_UNAVAILABLE로 응답합니다.")
  public ApiResponse<Response> analyze(@AuthenticationPrincipal FocusOnPrincipal principal,
      @PathVariable String sessionId,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
      @Valid @RequestBody Request request) {
    return ApiResponse.of(analyses.analyze(principal, deviceId, sessionId, request));
  }
}
