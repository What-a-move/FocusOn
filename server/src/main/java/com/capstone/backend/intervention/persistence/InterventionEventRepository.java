package com.capstone.backend.intervention.persistence;

import com.capstone.backend.intervention.dto.InterventionDtos.Type;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 호출자는 user/session 잠금을 먼저 획득한다. */
public interface InterventionEventRepository extends JpaRepository<InterventionEventEntity, Long> {
  /** 세션에서 실제 전송이 확인된 지정 유형의 이벤트 수를 반환한다. */
  long countBySessionIdAndTypeAndDeliveryConfirmedAtIsNotNull(Long sessionId, Type type);

  /** 이벤트 생성 순서로 조회하며 사용자/세션 소유권은 서비스에서 검증한다. */
  List<InterventionEventEntity> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId);
}
