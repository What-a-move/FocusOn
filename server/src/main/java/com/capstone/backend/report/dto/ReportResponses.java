package com.capstone.backend.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 실제 종료 세션의 시간과 시작 당시 목표를 제공한다. */
public final class ReportResponses {
  private ReportResponses() {}

  /** 월 전체 날짜를 포함하며 활동일 수는 하루 합계 1분을 기준으로 한다. */
  public record Calendar(String month, long studyDayCount, List<CalendarDay> days) {}

  /** 학습 기록이 없는 날짜도 0으로 명시한다. */
  public record CalendarDay(LocalDate date, long totalActiveMs, long sessionCount,
      boolean hasStudyActivity) {}

  /** 목록과 합계는 동일한 종료 스냅샷 조회 결과를 사용한다. */
  public record Daily(LocalDate date, long totalActiveMs, List<DailySession> sessions) {}

  /** summary의 goalSnapshot과 별개인 기존 일간 응답 필드를 유지한다. */
  public record DailySession(String sessionId, String goalTextSnapshot, Instant startedAt,
      Instant endedAt, long accumulatedActiveMs) {}
}
