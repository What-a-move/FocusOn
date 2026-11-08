package com.capstone.backend.intervention.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
