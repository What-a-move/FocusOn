package com.capstone.backend.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.report.persistence.ReportReadRepository;
import com.capstone.backend.report.service.ReportService;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.SessionStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/** DB query로 사용자·고정 날짜·최종 상태 필터와 합계 자료형을 검증한다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class ReportReadRepositoryTests {
  private static final LocalDate DAY = LocalDate.of(2026, 2, 28);
  private static final Instant START = Instant.parse("2026-02-28T14:59:00Z");
  @Autowired private EntityManager entities;
  @Autowired private ReportReadRepository reports;
  @Autowired private ReportService service;

  @Test
  void calendarFillsLeapMonthAndUsesCombinedMinuteThreshold() {
    var owner = owner();
    session(owner, SessionStatus.ENDED, DAY, 30000);
    session(owner, SessionStatus.ABANDONED, DAY, 30000);
    session(owner, SessionStatus.ENDED, DAY.minusDays(1), 59999);
    session(owner, SessionStatus.ENDED, DAY.minusDays(2), 0);
    entities.flush();
    var principal = new FocusOnPrincipal(owner.getId(), 1, 1,
        UserDeviceEntity.DeviceType.DESKTOP);
    var calendar = service.calendar(principal, "2026-02");
    assertThat(calendar.days()).hasSize(28);
    assertThat(calendar.studyDayCount()).isEqualTo(1);
    assertThat(calendar.days().getFirst().sessionCount()).isZero();
    assertThat(calendar.days().getLast().totalActiveMs()).isEqualTo(60000);
    assertThat(calendar.days().getLast().hasStudyActivity()).isTrue();
    assertThat(calendar.days().get(26).hasStudyActivity()).isFalse();
    assertThat(calendar.days().get(25).sessionCount()).isEqualTo(1);
    assertThat(service.calendar(principal, "2024-02").days()).hasSize(29);
    assertThat(service.calendar(principal, "2026-12").days()).hasSize(31);
  }

  @Test
  void dailyOrdersEqualStartTimesByNumericIdAndSharesListTotal() {
    var owner = owner();
    session(owner, SessionStatus.ENDED, DAY, 123);
    var laterId = session(owner, SessionStatus.ABANDONED, DAY, 456);
    entities.flush();
    var principal = new FocusOnPrincipal(owner.getId(), 1, 1,
        UserDeviceEntity.DeviceType.DESKTOP);
    var daily = service.daily(principal, DAY.toString());
    assertThat(daily.sessions()).hasSize(2);
    assertThat(daily.sessions().getFirst().sessionId()).isEqualTo(laterId.getId().toString());
    assertThat(daily.totalActiveMs()).isEqualTo(579);
    assertThat(service.daily(principal, DAY.plusDays(1).toString()).sessions()).isEmpty();
  }

  @Test
  void onlyOwnTerminalSessionsOnFrozenDateAreSummed() {
    var owner = owner();
    session(owner, SessionStatus.ENDED, DAY, 30000);
    session(owner, SessionStatus.ABANDONED, DAY, 30000);
    var active = session(owner, SessionStatus.RUNNING, DAY, 999999);
    for (var status : SessionStatus.values()) {
      if (status != SessionStatus.ENDED && status != SessionStatus.ABANDONED) {
        ReflectionTestUtils.setField(active, "status", status);
        entities.flush();
        assertThat(reports.findDaily(owner.getId(), DAY)).hasSize(2);
      }
    }
    session(owner(), SessionStatus.ENDED, DAY, 999999);
    session(owner, SessionStatus.ENDED, DAY.minusDays(1), 999999);
    session(owner, SessionStatus.ENDED, DAY.plusDays(1), 999999);
    entities.flush();
    entities.clear();
    var totals = reports.summarize(owner.getId(), DAY, DAY.plusDays(1));
    assertThat(totals).hasSize(1);
    assertThat(totals.getFirst().getDate()).isEqualTo(DAY);
    assertThat(totals.getFirst().getTotalActiveMs()).isEqualTo(60000);
    assertThat(totals.getFirst().getSessionCount()).isEqualTo(2);
    assertThat(reports.findDaily(owner.getId(), DAY)).hasSize(2);
  }

  @Test
  void snapshotsAndConfirmedTimeSurviveGoalChangesAndMidnight() {
    var owner = owner();
    var session = session(owner, SessionStatus.ABANDONED, DAY, 4321);
    session.getGoal().updateText("바뀐 목표", START.plusSeconds(100));
    session.getGoal().delete(START.plusSeconds(101));
    entities.flush();
    entities.clear();
    var rows = reports.findDaily(owner.getId(), DAY);
    assertThat(rows).hasSize(1);
    var row = rows.getFirst();
    assertThat(row.getGoalTextSnapshot()).isEqualTo("저장된 목표");
    assertThat(row.getAccumulatedActiveMs()).isEqualTo(4321);
    assertThat(row.getStartedAt()).isEqualTo(START);
    assertThat(row.getEndedAt()).isEqualTo(START.plusSeconds(7200));
    assertThat(reports.findDaily(owner.getId(), DAY.plusDays(1))).isEmpty();
  }

  @Test
  void aggregatesUseLongAndEmptyRangesReturnNoRows() {
    var owner = owner();
    session(owner, SessionStatus.ENDED, DAY, 3_000_000_000L);
    session(owner, SessionStatus.ABANDONED, DAY, 3_000_000_000L);
    entities.flush();
    assertThat(reports.summarize(owner.getId(), DAY, DAY.plusDays(1))
        .getFirst().getTotalActiveMs()).isEqualTo(6_000_000_000L);
    assertThat(reports.summarize(owner.getId(), DAY.plusDays(1), DAY.plusDays(2))).isEmpty();
    assertThat(reports.findDaily(owner.getId(), DAY.plusDays(1))).isEmpty();
  }

  private UserEntity owner() {
    var user = new UserEntity();
    entities.persist(user);
    return user;
  }

  private LearningSessionEntity session(UserEntity user, SessionStatus status, LocalDate date,
      long activeMs) {
    var device = new UserDeviceEntity();
    device.setUser(user);
    device.setDeviceType(UserDeviceEntity.DeviceType.DESKTOP);
    device.setInstallationIdHash(UUID.randomUUID().toString().replace("-", "").repeat(2));
    device.setDisplayName("리포트 검증");
    entities.persist(device);
    var goal = GoalEntity.create(user, "저장된 목표",
        GoalEntity.ConfirmationSource.MANUAL_CONFIRMED, START);
    entities.persist(goal);
    var session = LearningSessionEntity.create(user, goal, device, 300000,
        "Asia/Seoul", date, "{}", "v1", START);
    ReflectionTestUtils.setField(session, "status", status);
    ReflectionTestUtils.setField(session, "accumulatedActiveMs", activeMs);
    ReflectionTestUtils.setField(session, "endedAt", START.plusSeconds(7200));
    entities.persist(session);
    return session;
  }
}
