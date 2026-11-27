package com.capstone.backend.session.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 세션의 일시정지·재개 구간 하나다(#317). 재개 없이 끝나면 resumedAt은 null이다. */
@Entity
@Table(name = "session_pauses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionPauseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "session_id", nullable = false)
  private Long sessionId;
  @Column(name = "paused_at", nullable = false)
  private Instant pausedAt;
  @Column(name = "resumed_at")
  private Instant resumedAt;
  @Column(name = "pause_reason", nullable = false, length = 32)
  private String pauseReason;

  /** 서버 시각으로 새 일시정지 구간을 연다. */
  public static SessionPauseEntity open(long sessionId, Instant pausedAt, String reason) {
    var entity = new SessionPauseEntity();
    entity.sessionId = sessionId;
    entity.pausedAt = pausedAt;
    entity.pauseReason = reason;
    return entity;
  }

  /** 재개 시각으로 구간을 닫는다. 시계가 뒤로 가도 시작보다 앞서지 않게 한다. */
  public void close(Instant resumedAt) {
    this.resumedAt = resumedAt.isBefore(pausedAt) ? pausedAt : resumedAt;
  }
}
