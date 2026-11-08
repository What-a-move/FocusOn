package com.capstone.backend.intervention.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 호출자는 user/session 잠금을 먼저 획득한다. */
public interface InterventionEventRepository extends JpaRepository<InterventionEventEntity, Long> {
  /** 이벤트 생성 순서로 조회하며 사용자/세션 소유권은 서비스에서 검증한다. */
  List<InterventionEventEntity> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId);
}
