package com.capstone.backend.session.service;

import com.capstone.backend.session.persistence.SessionPauseEntity;
import com.capstone.backend.session.persistence.SessionPauseRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 세션 일시정지 구간을 여닫는다(#317). 호출하는 쪽의 세션 잠금 트랜잭션 안에서만 쓴다.
 * 세션마다 열린 구간은 하나(DB 부분 유니크)이므로, 재개 기록 없이 남은 구간이 있으면 새 구간을
 * 열기 전에 그 시각으로 닫는다.
 */
@Component
@RequiredArgsConstructor
public class SessionPauseRecorder {
  private final SessionPauseRepository pauses;

  /** 서버 시각으로 새 일시정지 구간을 연다. */
  public void open(long sessionId, Instant pausedAt, String reason) {
    var left = pauses.findFirstBySessionIdAndResumedAtIsNull(sessionId);
    if (left.isPresent()) {
      left.get().close(pausedAt);
      // 삽입이 갱신보다 먼저 나가면 열린 구간 유니크 인덱스에 걸리므로 닫기를 먼저 반영한다.
      pauses.flush();
    }
    pauses.save(SessionPauseEntity.open(sessionId, pausedAt, reason));
  }

  /** 열린 구간을 재개 시각으로 닫는다. 없으면(도입 전 일시정지) 아무것도 하지 않는다. */
  public void close(long sessionId, Instant resumedAt) {
    pauses.findFirstBySessionIdAndResumedAtIsNull(sessionId)
        .ifPresent(pause -> pause.close(resumedAt));
  }
}
