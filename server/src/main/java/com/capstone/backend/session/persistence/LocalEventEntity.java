package com.capstone.backend.session.persistence;

import com.capstone.backend.session.dto.LocalEventDtos.Code;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 동일 이벤트의 재전송이 새 실행 구간을 멈추지 않도록 수신 이력을 보존한다. */
@Entity
@Table(name = "session_local_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "session_id", nullable = false)
  private Long sessionId;
  @Column(name = "device_id", nullable = false)
  private Long deviceId;
  @Enumerated(EnumType.STRING)
  @Column(name = "event_code", nullable = false, length = 64)
  private Code code;
  @Column(name = "event_hash", nullable = false, length = 64)
  private String eventHash;
  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  /** 해시는 정규화된 나노초 시각을 포함하고 DB 시각 정밀도에 의존하지 않는다. */
  public static LocalEventEntity create(long sessionId, long deviceId, Code code,
      String hash, Instant occurred, Instant received) {
    var event = new LocalEventEntity();
    event.sessionId = sessionId;
    event.deviceId = deviceId;
    event.code = code;
    event.eventHash = hash;
    event.occurredAt = occurred;
    event.receivedAt = received;
    return event;
  }
}
