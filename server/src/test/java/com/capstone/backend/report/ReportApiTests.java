package com.capstone.backend.report;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 실제 Bearer·기기 검증과 공통 JSON 응답을 거쳐 두 조회 API를 검증한다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class ReportApiTests {
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
  void actualSnapshotFieldsAndOtherUsersAreIsolated() throws Exception {
    var owner = identity(DeviceType.DESKTOP);
    var stranger = identity(DeviceType.DESKTOP);
    terminal(owner, 60000);
    terminal(stranger, 999999);
    entities.flush();
    mvc.perform(get("/api/v1/reports/daily").param("date", "2026-11-01")
            .header("Authorization", owner.token()).header("X-Device-Id", owner.device().getId()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalActiveMs").value(60000))
        .andExpect(jsonPath("$.data.sessions.length()").value(1))
        .andExpect(jsonPath("$.data.sessions[0].goalTextSnapshot").value("리포트 목표"))
        .andExpect(jsonPath("$.data.sessions[0].accumulatedActiveMs").value(60000))
        .andExpect(jsonPath("$.data.sessions[0].sessionId").isString())
        .andExpect(jsonPath("$.meta").doesNotExist());
    mvc.perform(get("/api/v1/reports/calendar").param("month", "2026-11")
            .header("Authorization", owner.token()).header("X-Device-Id", owner.device().getId()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.days.length()").value(30))
        .andExpect(jsonPath("$.data.studyDayCount").value(1))
        .andExpect(jsonPath("$.data.days[0].hasStudyActivity").value(true))
        .andExpect(jsonPath("$.data.days[1].totalActiveMs").value(0));
  }

  @Test
  void invalidOrMissingQueriesAndDeviceHeadersAreRejected() throws Exception {
    var owner = identity(DeviceType.DESKTOP);
    for (var invalid : new String[] {"", "2026-02-29", "2024-02-30", "2026-1-01", "0000-01-01"}) {
      mvc.perform(get("/api/v1/reports/daily").param("date", invalid)
              .header("Authorization", owner.token())
              .header("X-Device-Id", owner.device().getId()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    for (var invalid : new String[] {"", "2026-13", "2026-1", "0000-01"}) {
      mvc.perform(get("/api/v1/reports/calendar").param("month", invalid)
              .header("Authorization", owner.token())
              .header("X-Device-Id", owner.device().getId()))
          .andExpect(status().isBadRequest());
    }
    for (var path : new String[] {"daily", "calendar"}) {
      mvc.perform(get("/api/v1/reports/" + path).header("Authorization", owner.token())
              .header("X-Device-Id", owner.device().getId())).andExpect(status().isBadRequest());
    }
    mvc.perform(get("/api/v1/reports/daily").param("date", "2024-02-29")
            .header("Authorization", owner.token()))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/reports/daily").param("date", "2024-02-29")
            .header("Authorization", owner.token()).header("X-Device-Id", "99999999"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anonymousAndLinkedExtensionCannotReadReports() throws Exception {
    mvc.perform(get("/api/v1/reports/daily").param("date", "2026-11-01"))
        .andExpect(status().isUnauthorized());
    var extension = identity(DeviceType.CHROME_EXTENSION);
    for (var path : new String[] {"daily", "calendar"}) {
      mvc.perform(get("/api/v1/reports/" + path).param("date", "2026-11-01")
              .param("month", "2026-11").header("Authorization", extension.token())
              .header("X-Device-Id", extension.device().getId()))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void noRecordsAndValidLeapDayAreSuccessfulEmptyReports() throws Exception {
    var owner = identity(DeviceType.DESKTOP);
    mvc.perform(get("/api/v1/reports/daily").param("date", "2024-02-29")
            .header("Authorization", owner.token()).header("X-Device-Id", owner.device().getId()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalActiveMs").value(0))
        .andExpect(jsonPath("$.data.sessions").isEmpty());
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

  private void terminal(Identity identity, long activeMs) {
    var now = Instant.parse("2026-11-01T00:00:00Z");
    var goal = GoalEntity.create(identity.user(), "리포트 목표",
        GoalEntity.ConfirmationSource.MANUAL_CONFIRMED, now);
    entities.persist(goal);
    var session = LearningSessionEntity.create(identity.user(), goal, identity.device(),
        300000, "Asia/Seoul", LocalDate.of(2026, 11, 1), "{}", "v1", now);
    ReflectionTestUtils.setField(session, "status", SessionStatus.ENDED);
    ReflectionTestUtils.setField(session, "accumulatedActiveMs", activeMs);
    ReflectionTestUtils.setField(session, "endedAt", now.plusSeconds(60));
    entities.persist(session);
  }

  private record Identity(UserEntity user, UserDeviceEntity device, String token) {}
}
