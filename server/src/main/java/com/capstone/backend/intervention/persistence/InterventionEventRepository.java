package com.capstone.backend.intervention.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 호출자는 user/session 잠금을 먼저 획득한다. */
public interface InterventionEventRepository extends JpaRepository<InterventionEventEntity, Long> {
  /** 보관 기한이 지난 판정 링크만 정리한다. 개입 이벤트는 보존한다. */
  @Modifying
  @Query("update InterventionEventEntity e set e.analysisRunId = null "
      + "where e.sessionId = :sessionId and e.analysisRunId in :runIds")
  int clearExpiredAnalysisLinks(@Param("sessionId") long sessionId,
      @Param("runIds") List<Long> runIds);

  /** 이벤트 생성 순서로 조회하며 사용자/세션 소유권은 서비스에서 검증한다. */
  List<InterventionEventEntity> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId);
}
