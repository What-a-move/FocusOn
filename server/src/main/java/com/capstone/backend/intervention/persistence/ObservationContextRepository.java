package com.capstone.backend.intervention.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 호출자는 사용자→세션 잠금을 먼저 획득한다. */
public interface ObservationContextRepository
    extends JpaRepository<ObservationContextEntity, Long> {
  /** 기기별 분석 번호 대신 Desktop이 명시한 전경 순번으로 선택한다. */
  Optional<ObservationContextEntity> findFirstBySessionIdOrderByForegroundSequenceDesc(
      Long sessionId);

  /** 재시도는 과거의 같은 context를 반환하며 새 전경으로 승격하지 않는다. */
  Optional<ObservationContextEntity> findBySessionIdAndClientContextId(Long sessionId, UUID nonce);
}
