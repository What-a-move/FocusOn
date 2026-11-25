package com.capstone.backend.session.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 세션 일시정지 구간을 시간 순서로 읽고 열린 구간을 찾는다. */
public interface SessionPauseRepository extends JpaRepository<SessionPauseEntity, Long> {
  /** 리포트 가로축용 전체 구간이다. 시작 시각, 같은 시각이면 저장 순서다. */
  List<SessionPauseEntity> findBySessionIdOrderByPausedAtAscIdAsc(Long sessionId);

  /** 아직 재개하지 않은 구간이다. 세션마다 하나뿐이다. */
  Optional<SessionPauseEntity> findFirstBySessionIdAndResumedAtIsNull(Long sessionId);
}
