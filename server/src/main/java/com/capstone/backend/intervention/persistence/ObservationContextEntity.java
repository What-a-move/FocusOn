package com.capstone.backend.intervention.persistence;

import com.capstone.backend.intervention.dto.ObservationContextDtos.Observer;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 원문 없이 전경 위임 이력과 서버 lease만 보존한다. */
@Entity
@Table(name = "session_observation_contexts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_context_nonce", columnNames = {"session_id", "client_context_id"}),
    @UniqueConstraint(name = "uq_context_sequence",
        columnNames = {"session_id", "foreground_sequence"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ObservationContextEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "session_id", nullable = false)
  private Long sessionId;
  @Column(name = "runner_device_id", nullable = false)
  private Long runnerDeviceId;
  @Column(name = "observer_device_id")
  private Long observerDeviceId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Observer observer;
  @Column(name = "client_context_id", nullable = false)
  private UUID clientContextId;
  @Column(name = "foreground_sequence", nullable = false)
  private long foregroundSequence;
  @Column(name = "running_since", nullable = false)
  private Instant runningSince;
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
  @Column(name = "valid_until", nullable = false)
  private Instant validUntil;

  /** 사용자/세션 잠금을 획득한 서비스에서만 호출한다. */
  public static ObservationContextEntity create(long sessionId, long runnerId, Long observerId,
      Observer observer, UUID nonce, long sequence, Instant runningSince, Instant now) {
    var value = new ObservationContextEntity();
    value.sessionId = sessionId;
    value.runnerDeviceId = runnerId;
    value.observerDeviceId = observerId;
    value.observer = observer;
    value.clientContextId = nonce;
    value.foregroundSequence = sequence;
    value.runningSince = runningSince;
    value.createdAt = now;
    value.validUntil = now.plusSeconds(30);
    return value;
  }

  /** 만료된 lease는 재전송으로 되살리지 않는다. */
  public void renew(Instant now) {
    if (validUntil.isAfter(now)) {
      validUntil = now.plusSeconds(30);
    }
  }
}
