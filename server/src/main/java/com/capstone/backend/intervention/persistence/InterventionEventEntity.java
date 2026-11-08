package com.capstone.backend.intervention.persistence;

import com.capstone.backend.intervention.dto.InterventionDtos.Action;
import com.capstone.backend.intervention.dto.InterventionDtos.Status;
import com.capstone.backend.intervention.dto.InterventionDtos.Type;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 원인 revision의 중복과 같은 세션/타입의 미완료 개입 중복을 DB에서도 막는다. */
@Entity
@Table(name = "intervention_events", uniqueConstraints = {
    @UniqueConstraint(name = "uq_intervention_cause",
        columnNames = {"session_id", "type", "cause"}),
    @UniqueConstraint(name = "uq_intervention_pending",
        columnNames = {"session_id", "type", "pending_slot"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterventionEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "session_id", nullable = false)
  private Long sessionId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Type type;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Status status;
  @Column(nullable = false)
  private long cause;
  @Column(name = "pending_slot")
  private Integer pendingSlot;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "channels_json", nullable = false, columnDefinition = "jsonb")
  private String channelsJson;
  @Column(name = "policy_version", nullable = false, length = 64)
  private String policyVersion;
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
  @Column(name = "running_since", nullable = false)
  private Instant runningSince;
  @Column(name = "delivery_confirmed_at")
  private Instant deliveryConfirmedAt;
  @Column(name = "acted_at")
  private Instant actedAt;

  /** 승인된 수명 30초를 적용한다. 생성 시각은 실제 전송 시각이 아니다. */
  public static InterventionEventEntity offTask(long sessionId, long revision, String channels,
      String policyVersion, Instant runningSince, Instant now) {
    var event = new InterventionEventEntity();
    event.sessionId = sessionId;
    event.type = Type.OFF_TASK;
    event.status = Status.PENDING;
    event.pendingSlot = 1;
    event.cause = revision;
    event.channelsJson = channels;
    event.policyVersion = policyVersion;
    event.runningSince = runningSince;
    event.createdAt = now;
    event.expiresAt = now.plusSeconds(30);
    return event;
  }

  /** 반응/전송 성공을 추정하지 않고 미전송 후보를 만료한다. */
  public void expire() {
    if (status == Status.PENDING) {
      status = Status.EXPIRED;
      pendingSlot = null;
    }
  }

  /** 첫 사용자 결정을 유지하며 재전송이 actedAt을 연장하지 않는다. */
  public void acknowledge(Action action, Instant now) {
    if (status == Status.PENDING) {
      status = Status.valueOf(action.name());
      pendingSlot = null;
      actedAt = now;
    }
  }
}
