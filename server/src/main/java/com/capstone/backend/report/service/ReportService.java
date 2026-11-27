package com.capstone.backend.report.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.report.dto.ReportResponses.Calendar;
import com.capstone.backend.report.dto.ReportResponses.CalendarDay;
import com.capstone.backend.report.dto.ReportResponses.Daily;
import com.capstone.backend.report.dto.ReportResponses.DailySession;
import com.capstone.backend.report.dto.ReportResponses.Progress;
import com.capstone.backend.report.dto.ReportResponses.Streak;
import com.capstone.backend.report.dto.ReportResponses.Today;
import com.capstone.backend.report.dto.ReportResponses.Week;
import com.capstone.backend.report.dto.ReportResponses.WeekDay;
import com.capstone.backend.report.persistence.ReportReadRepository;
import com.capstone.backend.report.persistence.ReportReadRepository.SessionRow;
import com.capstone.backend.report.persistence.ReportReadRepository.TargetRow;
import com.capstone.backend.user.persistence.UserFocusSettingsEntity;
import com.capstone.backend.user.persistence.UserFocusSettingsRepository;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GET에서는 시간과 상태를 확정하지 않고 이미 종료된 저장값만 집계한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
  private final ReportReadRepository reports;
  private final UserFocusSettingsRepository focusSettings;
  /** 기록이 없을 때 진행 요약의 기본 날짜를 정하는 시간대다. */
  private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Seoul");
  private static final LocalDate EPOCH_DAY = LocalDate.of(1, 1, 1);

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
    var stats = dayStats(reports.findTargetRows(principal.userId(), first, end));
    var days = first.datesUntil(end).map(date -> {
      var row = totals.get(date);
      long active = row == null ? 0 : row.getTotalActiveMs();
      var stat = stats.get(date);
      return new CalendarDay(date, active, row == null ? 0 : row.getSessionCount(),
          active >= 60000, stat != null && stat.achieved());
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

  /**
   * 홈·메뉴바·위젯용 진행 요약이다(#374). date가 없으면 가장 최근 세션의 시간대(없으면 Asia/Seoul)로
   * 오늘을 정한다. 확정 ENDED·ABANDONED 세션만 집계하고 진행 중 세션은 Desktop이 더해 보여 준다.
   */
  public Progress progress(FocusOnPrincipal principal, String value) {
    desktop(principal);
    LocalDate date = value == null ? today(principal.userId()) : parseDate(value);
    var settings = focusSettings.findById(principal.userId()).orElse(null);
    long currentTarget = settings == null ? UserFocusSettingsEntity.DEFAULT_DAILY_TARGET_MS
        : settings.getDailyTargetMs();
    // 연속 기록을 위해 기준일까지의 전체 종료 세션을 읽는다(사용자 한 명의 세션 수 범위).
    var stats = dayStats(reports.findTargetRows(principal.userId(), EPOCH_DAY,
        date.plusDays(1)));
    var todayStat = stats.get(date);
    long todayTotal = todayStat == null ? 0 : todayStat.total();
    // 그날 세션이 있으면 첫 세션의 고정 목표, 없으면 지금 설정을 보여 준다.
    long todayTarget = todayStat == null ? currentTarget : todayStat.target();
    LocalDate weekStart = date.with(DayOfWeek.MONDAY);
    var weekDays = weekStart.datesUntil(weekStart.plusDays(7)).map(day -> {
      var stat = stats.get(day);
      return new WeekDay(day, stat == null ? 0 : stat.total());
    }).toList();
    long weekTotal = weekDays.stream().mapToLong(WeekDay::totalActiveMs).sum();
    return new Progress(date,
        new Today(todayTotal, todayTarget, todayStat != null && todayStat.achieved()),
        new Week(weekStart, weekTotal, settings == null ? null : settings.getWeeklyTargetMs(),
            weekDays),
        streak(stats, date));
  }

  /** 어제까지 이어진 달성 일수에 오늘 달성을 더한다. 최장 기록은 기준일까지의 전체다. */
  private static Streak streak(Map<LocalDate, DayStat> stats, LocalDate date) {
    long current = 0;
    for (LocalDate day = date.minusDays(1); achieved(stats, day); day = day.minusDays(1)) {
      current++;
    }
    if (achieved(stats, date)) {
      current++;
    }
    long longest = 0;
    long run = 0;
    LocalDate previous = null;
    for (LocalDate day : stats.keySet().stream().filter(d -> !d.isAfter(date)).sorted()
        .toList()) {
      if (!stats.get(day).achieved()) {
        run = 0;
        previous = day;
        continue;
      }
      run = previous != null && previous.plusDays(1).equals(day) && run > 0 ? run + 1 : 1;
      longest = Math.max(longest, run);
      previous = day;
    }
    return new Streak(current, Math.max(longest, current));
  }

  private static boolean achieved(Map<LocalDate, DayStat> stats, LocalDate day) {
    var stat = stats.get(day);
    return stat != null && stat.achieved();
  }

  /**
   * 날짜별 합계와 그날 첫 세션(시작 시각, 같으면 ID 순)의 고정 하루 목표다. 도입 전 세션은 기본 60분이다.
   * 목표를 바꿔도 지난 날짜의 달성 여부는 바뀌지 않는다.
   */
  private static Map<LocalDate, DayStat> dayStats(List<TargetRow> rows) {
    Map<LocalDate, TargetRow> first = new HashMap<>();
    Map<LocalDate, Long> totals = new HashMap<>();
    Comparator<TargetRow> order = Comparator.comparing(TargetRow::getStartedAt)
        .thenComparing(TargetRow::getSessionId);
    for (TargetRow row : rows) {
      totals.merge(row.getStudyDate(), row.getAccumulatedActiveMs(), Long::sum);
      first.merge(row.getStudyDate(), row, (a, b) -> order.compare(a, b) <= 0 ? a : b);
    }
    Map<LocalDate, DayStat> stats = new HashMap<>();
    totals.forEach((day, total) -> {
      Long snapshot = first.get(day).getDailyTargetMsSnapshot();
      long target = snapshot == null ? UserFocusSettingsEntity.DEFAULT_DAILY_TARGET_MS : snapshot;
      stats.put(day, new DayStat(total, target, total >= target));
    });
    return stats;
  }

  private record DayStat(long total, long target, boolean achieved) {}

  private LocalDate today(long userId) {
    ZoneId zone = reports.findRecentTimeZones(userId, PageRequest.of(0, 1)).stream().findFirst()
        .flatMap(id -> {
          try {
            return java.util.Optional.of(ZoneId.of(id));
          } catch (DateTimeException exception) {
            return java.util.Optional.empty();
          }
        }).orElse(FALLBACK_ZONE);
    return Instant.now().atZone(zone).toLocalDate();
  }

  private static LocalDate parseDate(String value) {
    try {
      if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
        throw new IllegalArgumentException();
      }
      LocalDate date = LocalDate.parse(value);
      if (date.getYear() < 1) {
        throw new IllegalArgumentException();
      }
      return date;
    } catch (DateTimeException | IllegalArgumentException exception) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
  }

  private void desktop(FocusOnPrincipal principal) {
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.SESSION_QUERY_FORBIDDEN);
    }
  }
}
