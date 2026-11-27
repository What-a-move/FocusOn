package com.capstone.backend.report.controller;

import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiResponse;
import com.capstone.backend.report.dto.ReportResponses.Calendar;
import com.capstone.backend.report.dto.ReportResponses.Daily;
import com.capstone.backend.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 Desktop의 일간·달력 리포트를 제공한다. */
@RestController
@RequiredArgsConstructor
@Tag(name = "리포트")
@SecurityRequirement(name = "focusOnBearer")
public class ReportController {
  private final ReportService reports;

  /** 세션 시작일에 고정된 종료 기록만 월별로 집계한다. */
  @GetMapping("/api/v1/reports/calendar")
  @Operation(summary = "집중 달력 조회",
      description = "Desktop 전용. month=YYYY-MM 필수. 월 전체 날짜와 하루 1분 이상 활동일 수입니다.")
  public ApiResponse<Calendar> calendar(@AuthenticationPrincipal FocusOnPrincipal principal,
      @RequestParam String month,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
    DeviceHeaderValidator.require(deviceId, principal);
    return ApiResponse.of(reports.calendar(principal, month));
  }

  /** 오늘·이번 주 집중 시간과 목표, 연속 기록을 한 번에 돌려준다(#374). */
  @GetMapping("/api/v1/reports/progress")
  @Operation(summary = "진행 요약 조회",
      description = "Desktop 전용. date=YYYY-MM-DD 선택(없으면 최근 세션 시간대의 오늘). 확정 종료 세션만 "
          + "집계하며 주는 월요일 시작입니다. 연속 기록은 하루 목표를 이어서 달성한 일수입니다.")
  public ApiResponse<com.capstone.backend.report.dto.ReportResponses.Progress> progress(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @RequestParam(required = false) String date,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
    DeviceHeaderValidator.require(deviceId, principal);
    return ApiResponse.of(reports.progress(principal, date));
  }

  /** 진행 중 세션이나 추정 시간을 포함하지 않는다. */
  @GetMapping("/api/v1/reports/daily")
  @Operation(summary = "일간 학습 리포트 조회",
      description = "Desktop 전용. date=YYYY-MM-DD 필수. 종료 세션을 시작 시각·ID 내림차순으로 반환합니다.")
  public ApiResponse<Daily> daily(@AuthenticationPrincipal FocusOnPrincipal principal,
      @RequestParam String date,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
    DeviceHeaderValidator.require(deviceId, principal);
    return ApiResponse.of(reports.daily(principal, date));
  }
}
