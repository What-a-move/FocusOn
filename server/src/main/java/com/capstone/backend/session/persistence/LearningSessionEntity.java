package com.capstone.backend.session.persistence;

import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.goal.persistence.GoalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 학습 세션과 시작 당시 확정된 설정 스냅샷이다. */
@Entity
@Table(name = "learning_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningSessionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "goal_id", nullable = false)
  private GoalEntity goal;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "runner_device_id", nullable = false)
  private UserDeviceEntity runnerDevice;

  @Column(name = "goal_text_snapshot", nullable = false, length = 40)
  private String goalTextSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SessionStatus status;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "target_duration_ms", nullable = false)
  private long targetDurationMs;

  @Column(name = "accumulated_active_ms", nullable = false)
  private long accumulatedActiveMs;

  @Column(name = "last_resumed_at")
  private Instant lastResumedAt;

  @Column(name = "last_heartbeat_at")
  private Instant lastHeartbeatAt;

  @Column(name = "pause_reason", length = 32)
  private String pauseReason;

  @Column(name = "end_reason", length = 32)
  private String endReason;

  @Column(name = "user_time_zone_snapshot", nullable = false, length = 64)
  private String userTimeZoneSnapshot;

  @Column(name = "study_date", nullable = false)
  private LocalDate studyDate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "session_options_snapshot_json", nullable = false, columnDefinition = "jsonb")
  private String sessionOptionsSnapshotJson;

  @Column(name = "intervention_policy_version", nullable = false, length = 64)
  private String interventionPolicyVersion;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  private LearningSessionEntity(UserEntity user, GoalEntity goal, UserDeviceEntity runnerDevice,
      long targetDurationMs, String userTimeZoneSnapshot, LocalDate studyDate,
      String sessionOptionsSnapshotJson, String interventionPolicyVersion, Instant startedAt) {
    this.user = user;
    this.goal = goal;
    this.runnerDevice = runnerDevice;
    this.goalTextSnapshot = goal.getGoalText();
    this.status = SessionStatus.RUNNING;
    this.targetDurationMs = targetDurationMs;
    this.userTimeZoneSnapshot = userTimeZoneSnapshot;
    this.studyDate = studyDate;
    this.sessionOptionsSnapshotJson = sessionOptionsSnapshotJson;
    this.interventionPolicyVersion = interventionPolicyVersion;
    this.startedAt = startedAt;
    this.lastResumedAt = startedAt;
    this.createdAt = startedAt;
  }

  /** 확정된 시작 값으로 세션 스냅샷을 만든다. */
  public static LearningSessionEntity create(UserEntity user, GoalEntity goal,
      UserDeviceEntity runnerDevice, long targetDurationMs, String userTimeZoneSnapshot,
      LocalDate studyDate, String sessionOptionsSnapshotJson, String interventionPolicyVersion,
      Instant startedAt) {
    return new LearningSessionEntity(user, goal, runnerDevice, targetDurationMs,
        userTimeZoneSnapshot, studyDate, sessionOptionsSnapshotJson, interventionPolicyVersion,
        startedAt);
  }
}
