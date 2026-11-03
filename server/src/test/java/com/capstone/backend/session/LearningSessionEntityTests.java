package com.capstone.backend.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 세션 시작 시 저장되는 초기 스냅샷을 확인한다. */
class LearningSessionEntityTests {
  @Test
  void createsRunningSessionWithStartSnapshots() {
    UserEntity user = mock(UserEntity.class);
    UserDeviceEntity device = mock(UserDeviceEntity.class);
    GoalEntity goal = mock(GoalEntity.class);
    when(goal.getGoalText()).thenReturn("Spring 공부");
    Instant startedAt = Instant.parse("2026-10-29T00:00:00Z");
    LocalDate studyDate = LocalDate.of(2026, 10, 29);

    var session = LearningSessionEntity.create(user, goal, device, 1_800_000L,
        "Asia/Seoul", studyDate, "{}", "v1", startedAt);

    assertThat(session.getUser()).isSameAs(user);
    assertThat(session.getGoal()).isSameAs(goal);
    assertThat(session.getRunnerDevice()).isSameAs(device);
    assertThat(session.getGoalTextSnapshot()).isEqualTo("Spring 공부");
    assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);
    assertThat(session.getVersion()).isZero();
    assertThat(session.getAccumulatedActiveMs()).isZero();
    assertThat(session.getLastResumedAt()).isEqualTo(startedAt);
    assertThat(session.getStudyDate()).isEqualTo(studyDate);
    assertThat(session.getSessionOptionsSnapshotJson()).isEqualTo("{}");
  }
}
