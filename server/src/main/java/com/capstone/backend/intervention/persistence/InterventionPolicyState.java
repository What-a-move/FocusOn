package com.capstone.backend.intervention.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 세션 잠금 아래 서버 시각 조각과 현재 분석 대기 ID를 내구성 있게 보존한다. */
@Entity
@Table(name = "intervention_policy_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterventionPolicyState {
  @Id
  @Column(name = "session_id")
  private Long sessionId;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "timeline_json", nullable = false, columnDefinition = "jsonb")
  private String timelineJson;
  @Column(nullable = false)
  private long revision;
  @Column(name = "far_goal_version", nullable = false)
  private long farGoalVersion = 1;
  @Column(name = "far_duration_ms", nullable = false)
  private long farDurationMs;
  @Column(name = "far_checkpoint_at")
  private Instant farCheckpointAt;

  /** 현재 전경의 확인된 공부 구간 안에서만 FAR 시간을 합산한다. */
  public boolean countConfirmedFar(long goalVersion, Instant now) {
    if (farGoalVersion != goalVersion) {
      farGoalVersion = goalVersion;
      farDurationMs = 0;
      farCheckpointAt = null;
    }
    long promptAfterMs = Duration.ofMinutes(10).toMillis();
    if (farCheckpointAt == null || now.isAfter(farCheckpointAt)) {
      if (farCheckpointAt != null) {
        farDurationMs = Math.min(promptAfterMs,
            farDurationMs + Duration.between(farCheckpointAt, now).toMillis());
      }
      farCheckpointAt = now;
    }
    return farDurationMs >= promptAfterMs;
  }

  /** 새 분석을 기다리기 직전까지 이미 확인된 FAR 시간을 합산하고 구간을 닫는다. */
  public void finishConfirmedFar(long goalVersion, Instant now) {
    if (farCheckpointAt != null && farGoalVersion == goalVersion) {
      countConfirmedFar(goalVersion, now);
    }
    suspendFar();
  }

  /** 전경 이동, 분석 대기, 보호 상태 및 실행 변경 중에는 시간을 쌓지 않는다. */
  public void suspendFar() {
    farCheckpointAt = null;
  }

  /** 외부 입력 JSON이 아닌 타입이 정해진 서버 snapshot만 저장한다. */
  public static InterventionPolicyState create(long sessionId, String timeline) {
    var state = new InterventionPolicyState();
    state.sessionId = sessionId;
    state.timelineJson = timeline;
    return state;
  }

  /** 현재 관측 전이가 있을 때만 원인 revision을 증가시킨다. */
  public void transition(String timeline) {
    timelineJson = timeline;
    revision++;
  }
}
