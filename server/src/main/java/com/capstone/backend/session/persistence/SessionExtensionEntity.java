package com.capstone.backend.session.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 세션에서 실제로 확정된 연장 이력이다. */
@Entity
@Table(name = "session_extensions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionExtensionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private LearningSessionEntity session;

  @Column(name = "additional_duration_ms", nullable = false)
  private long additionalDurationMs;

  @Column(name = "previous_target_duration_ms", nullable = false)
  private long previousTargetDurationMs;

  @Column(name = "target_duration_ms", nullable = false)
  private long targetDurationMs;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** 소유권·연장 시간을 검증한 세션의 변경 이력을 생성한다. */
  public static SessionExtensionEntity create(LearningSessionEntity session, long additional,
      long previousTarget, Instant now) {
    var record = new SessionExtensionEntity();
    record.session = session;
    record.additionalDurationMs = additional;
    record.previousTargetDurationMs = previousTarget;
    record.targetDurationMs = session.getTargetDurationMs();
    record.createdAt = now;
    return record;
  }
}
