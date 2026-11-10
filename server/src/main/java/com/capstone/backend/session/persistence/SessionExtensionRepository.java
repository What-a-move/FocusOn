package com.capstone.backend.session.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 확정된 연장 이력을 저장한다. */
public interface SessionExtensionRepository extends JpaRepository<SessionExtensionEntity, Long> {
  /** 종료된 세션의 실제 연장 이력을 한 번의 집계로 반환한다. */
  @Query("select count(e) as extensionCount, coalesce(sum(e.additionalDurationMs), 0) "
      + "as extendedDurationMs from SessionExtensionEntity e where e.session.id = :sessionId")
  ExtensionTotals summarize(@Param("sessionId") Long sessionId);

  /** 실제 이력의 건수와 추가 시간 합계다. */
  interface ExtensionTotals {
    long getExtensionCount();

    long getExtendedDurationMs();
  }
}
