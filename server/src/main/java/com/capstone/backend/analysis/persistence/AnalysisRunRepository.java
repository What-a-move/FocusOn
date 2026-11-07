package com.capstone.backend.analysis.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 중복 제출과 기기 내 source를 가로지르는 관측 순서를 조회한다. */
public interface AnalysisRunRepository extends JpaRepository<AnalysisRunEntity, Long> {
  /** clientRunId는 session 안에서 유일하다. */
  boolean existsBySessionIdAndClientRunId(Long sessionId, UUID clientRunId);

  /** 완료 순서 대신 observedAt 순으로 조회해 늦은 AI 결과가 최신 보호 상태를 덮지 않는다. */
  Optional<AnalysisRunEntity> findFirstBySessionIdAndDeviceIdOrderByObservedAtDescIdDesc(
      Long sessionId, Long deviceId);
}
