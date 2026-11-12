package com.capstone.backend.report.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.report.dto.ReportResponses.Calendar;
import com.capstone.backend.report.dto.ReportResponses.CalendarDay;
import com.capstone.backend.report.dto.ReportResponses.Daily;
import com.capstone.backend.report.dto.ReportResponses.DailySession;
import com.capstone.backend.report.persistence.ReportReadRepository;
import com.capstone.backend.report.persistence.ReportReadRepository.SessionRow;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GET에서는 시간과 상태를 확정하지 않고 이미 종료된 저장값만 집계한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
  private final ReportReadRepository reports;

  /** 기록이 없는 날을 포함한 월 전체 날짜를 오름차순으로 제공한다. */
  public Calendar calendar(FocusOnPrincipal principal, String value) {
    desktop(principal);
    YearMonth month;
    try {
      if (value == null || !value.matches("[0-9]{4}-[0-9]{2}")) {
        throw new IllegalArgumentException();
      }
      month = YearMonth.parse(value);
      if (month.getYear() < 1) {
        throw new IllegalArgumentException();
      }
    } catch (DateTimeException | IllegalArgumentException exception) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    var first = month.atDay(1);
    var end = month.plusMonths(1).atDay(1);
    var totals = reports.summarize(principal.userId(), first, end).stream()
        .collect(Collectors.toMap(ReportReadRepository.DayTotals::getDate, Function.identity()));
    var days = first.datesUntil(end).map(date -> {
      var row = totals.get(date);
      long active = row == null ? 0 : row.getTotalActiveMs();
      return new CalendarDay(date, active, row == null ? 0 : row.getSessionCount(),
          active >= 60000);
    }).toList();
    return new Calendar(value, days.stream().filter(CalendarDay::hasStudyActivity).count(), days);
  }

  /** 한 번의 목록 조회에서 합계를 계산하므로 동시 종료에 따른 별도 합계 불일치가 없다. */
  public Daily daily(FocusOnPrincipal principal, String value) {
    desktop(principal);
    LocalDate date;
    try {
      if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
        throw new IllegalArgumentException();
      }
      date = LocalDate.parse(value);
      if (date.getYear() < 1) {
        throw new IllegalArgumentException();
      }
    } catch (DateTimeException | IllegalArgumentException exception) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    var rows = reports.findDaily(principal.userId(), date).stream()
        .sorted(Comparator.comparing(SessionRow::getStartedAt)
            .thenComparing(SessionRow::getSessionId).reversed())
        .map(row -> new DailySession(row.getSessionId().toString(), row.getGoalTextSnapshot(),
            row.getStartedAt(), row.getEndedAt(), row.getAccumulatedActiveMs())).toList();
    long total = rows.stream().mapToLong(DailySession::accumulatedActiveMs).sum();
    return new Daily(date, total, rows);
  }

  private void desktop(FocusOnPrincipal principal) {
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.SESSION_QUERY_FORBIDDEN);
    }
  }
}
