package com.capstone.backend.report;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.AccessJwtService;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.common.api.RequestIdFilter;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.SessionStatus;
import com.capstone.backend.user.persistence.UserFocusSettingsEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 진행 요약·연속 기록과 달력의 하루 목표 달성을 검증한다(#374). */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class ReportProgressApiTests {
  @Autowired private EntityManager entities;
  @Autowired private AccessJwtService tokens;
  @Autowired private WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
  }

  @Test
  void progressAndCalendarUseFirstSessionTargetOfEachDay() throws Exception {
    var owner = identity(DeviceType.DESKTOP);
    final var stranger = identity(DeviceType.DESKTOP);
    settings(owner, 5400000L, 36000000L);
    // 11-01(일)~11-04(수) 4일 연속 달성(11-09는 월요일), 11-05 미달, 11-06 도입 전 세션(기본 60분) 달성,
    // 11-07 기록 없음, 11-08·11-09 달성. 11-04는 첫 세션 목표(30분)로 판정한다.
    session(owner, "2026-11-01", 0, 3600000, 3600000L, SessionStatus.ENDED);
    session(owner, "2026-11-02", 0, 3600000, 3600000L, SessionStatus.ABANDONED);
    session(owner, "2026-11-03", 0, 3600000, 3600000L, SessionStatus.ENDED);
    session(owner, "2026-11-04", 0, 1000000, 1800000L, SessionStatus.ENDED);
    session(owner, "2026-11-04", 3600, 1000000, 7200000L, SessionStatus.ENDED);
    session(owner, "2026-11-05", 0, 1000000, 3600000L, SessionStatus.ENDED);
    session(owner, "2026-11-06", 0, 3600000, null, SessionStatus.ENDED);
    session(owner, "2026-11-08", 0, 3600000, 3600000L, SessionStatus.ENDED);
    session(owner, "2026-11-09", 0, 2000000, 1800000L, SessionStatus.ENDED);
    // 진행 중 세션과 다른 사용자 기록은 넣지 않는다.
    session(owner, "2026-11-09", 7200, 9000000, 1800000L, SessionStatus.RUNNING);
    session(stranger, "2026-11-07", 0, 9000000, 600000L, SessionStatus.ENDED);
    entities.flush();

    get(owner, "/api/v1/reports/calendar?month=2026-11").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].dailyTargetAchieved").value(true))
        .andExpect(jsonPath("$.data.days[3].dailyTargetAchieved").value(true))
        .andExpect(jsonPath("$.data.days[4].dailyTargetAchieved").value(false))
        .andExpect(jsonPath("$.data.days[5].dailyTargetAchieved").value(true))
        .andExpect(jsonPath("$.data.days[6].dailyTargetAchieved").value(false))
        .andExpect(jsonPath("$.data.days[9].dailyTargetAchieved").value(false));

    get(owner, "/api/v1/reports/progress?date=2026-11-09").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.date").value("2026-11-09"))
        .andExpect(jsonPath("$.data.today.totalActiveMs").value(2000000))
        .andExpect(jsonPath("$.data.today.dailyTargetMs").value(1800000))
        .andExpect(jsonPath("$.data.today.achieved").value(true))
        .andExpect(jsonPath("$.data.week.weekStart").value("2026-11-09"))
        .andExpect(jsonPath("$.data.week.dailyTotals.length()").value(7))
        .andExpect(jsonPath("$.data.week.dailyTotals[0].date").value("2026-11-09"))
        .andExpect(jsonPath("$.data.week.dailyTotals[0].totalActiveMs").value(2000000))
        .andExpect(jsonPath("$.data.week.dailyTotals[6].date").value("2026-11-15"))
        .andExpect(jsonPath("$.data.week.totalActiveMs").value(2000000))
        .andExpect(jsonPath("$.data.week.weeklyTargetMs").value(36000000))
        .andExpect(jsonPath("$.data.streak.currentDays").value(2))
        .andExpect(jsonPath("$.data.streak.longestDays").value(4));
    // 기준일 기록이 없으면 지금 설정한 하루 목표를 보여 주고, 어제까지의 연속만 센다.
    get(owner, "/api/v1/reports/progress?date=2026-11-10").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.today.totalActiveMs").value(0))
        .andExpect(jsonPath("$.data.today.dailyTargetMs").value(5400000))
        .andExpect(jsonPath("$.data.today.achieved").value(false))
        .andExpect(jsonPath("$.data.streak.currentDays").value(2))
        .andExpect(jsonPath("$.data.week.weekStart").value("2026-11-09"))
        .andExpect(jsonPath("$.data.week.totalActiveMs").value(2000000));
    // 지난주(11-02~11-08) 합계는 진행 중 세션 없이 확정 세션만 더한다.
    get(owner, "/api/v1/reports/progress?date=2026-11-08").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.week.weekStart").value("2026-11-02"))
        .andExpect(jsonPath("$.data.week.totalActiveMs").value(17400000))
        .andExpect(jsonPath("$.data.streak.currentDays").value(1));
  }

  @Test
  void defaultDateEmptyUserAndValidation() throws Exception {
    var owner = identity(DeviceType.DESKTOP);
    String today = Instant.now().atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString();
    get(owner, "/api/v1/reports/progress").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.date").value(today))
        .andExpect(jsonPath("$.data.today.dailyTargetMs").value(3600000))
        .andExpect(jsonPath("$.data.week.weeklyTargetMs").isEmpty())
        .andExpect(jsonPath("$.data.streak.currentDays").value(0))
        .andExpect(jsonPath("$.data.streak.longestDays").value(0));
    for (var invalid : new String[] {"2026-02-29", "2026-1-01", "0000-01-01", "abc"}) {
      get(owner, "/api/v1/reports/progress?date=" + invalid).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/api/v1/reports/progress").header("Authorization", owner.token()))
        .andExpect(status().isBadRequest());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get("/api/v1/reports/progress")).andExpect(status().isUnauthorized());
    var extension = identity(DeviceType.CHROME_EXTENSION);
    get(extension, "/api/v1/reports/progress").andExpect(status().isForbidden());
  }

  private ResultActions get(Identity identity, String path) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get(path).header("Authorization", identity.token())
        .header("X-Device-Id", identity.device().getId()));
  }

  private void settings(Identity identity, long daily, Long weekly) {
    var settings = UserFocusSettingsEntity.create(identity.user().getId(),
        UserFocusSettingsEntity.InterventionLevel.NORMAL, "MOLE_UI", true, false, Instant.now());
    settings.applyRedesign("", true, daily, weekly, true, Instant.now());
    entities.persist(settings);
  }

  private void session(Identity identity, String studyDate, long offsetSeconds, long activeMs,
      Long target, SessionStatus status) {
    var started = Instant.parse(studyDate + "T00:00:00Z").plusSeconds(offsetSeconds);
    var goal = GoalEntity.create(identity.user(), "진행 목표",
        GoalEntity.ConfirmationSource.MANUAL_CONFIRMED, started);
    entities.persist(goal);
    var session = LearningSessionEntity.create(identity.user(), goal, identity.device(),
        300000, "Asia/Seoul", LocalDate.parse(studyDate), "{}", "v2", started);
    ReflectionTestUtils.setField(session, "status", status);
    ReflectionTestUtils.setField(session, "accumulatedActiveMs", activeMs);
    if (status != SessionStatus.RUNNING) {
      ReflectionTestUtils.setField(session, "endedAt", started.plusSeconds(60));
    }
    if (target != null) {
      session.snapshotDailyTarget(target);
    }
    entities.persist(session);
  }

  private Identity identity(DeviceType type) {
    var user = new UserEntity();
    entities.persist(user);
    var desktop = device(user, DeviceType.DESKTOP);
    family(user, desktop);
    var selected = desktop;
    if (type == DeviceType.CHROME_EXTENSION) {
      selected = device(user, type);
      selected.setLinkedDesktopDevice(desktop);
    }
    var family = type == DeviceType.DESKTOP
        ? entities.createQuery("select f from TokenFamilyEntity f where f.device = :device",
            TokenFamilyEntity.class).setParameter("device", selected).getSingleResult()
        : family(user, selected);
    entities.flush();
    return new Identity(user, selected,
        "Bearer " + tokens.issue(user.getId(), selected.getId(), family.getId()).token());
  }

  private UserDeviceEntity device(UserEntity user, DeviceType type) {
    var device = new UserDeviceEntity();
    device.setUser(user);
    device.setDeviceType(type);
    device.setInstallationIdHash(UUID.randomUUID().toString().replace("-", "").repeat(2));
    device.setDisplayName("리포트 검증");
    entities.persist(device);
    return device;
  }

  private TokenFamilyEntity family(UserEntity user, UserDeviceEntity device) {
    var family = new TokenFamilyEntity();
    family.setUser(user);
    family.setDevice(device);
    family.setExpiresAt(Instant.now().plusSeconds(3600));
    entities.persist(family);
    return family;
  }

  private record Identity(UserEntity user, UserDeviceEntity device, String token) {}
}
