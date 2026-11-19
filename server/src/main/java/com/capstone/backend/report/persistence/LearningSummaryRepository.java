package com.capstone.backend.report.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 세션별 단일 정리와 생성 작업 대기열에 접근한다. */
public interface LearningSummaryRepository extends JpaRepository<LearningSummaryEntity, Long> {
  /** 실행 시각이 된 대기 작업과 점유가 만료된 생성 작업을 찾는다. 점유는 claim이 확정한다. */
  @Query("select s.sessionId from LearningSummaryEntity s where "
      + "(s.status = com.capstone.backend.report.dto.LearningSummaryResponse.Status.QUEUED "
      + "and s.nextAttemptAt <= :now) or "
      + "(s.status = com.capstone.backend.report.dto.LearningSummaryResponse.Status.GENERATING "
      + "and s.leaseExpiresAt <= :now and s.attemptCount < :maxAttempts) order by s.sessionId")
  List<Long> findClaimable(@Param("now") Instant now, @Param("maxAttempts") short maxAttempts,
      Pageable page);

  /**
   * 조건부 갱신 한 번으로 작업을 점유한다. 동시에 여러 Worker가 같은 행을 갱신하면 먼저
   * 커밋한 쪽만 조건을 만족하므로 1을 받은 Worker만 생성을 진행한다.
   */
  @Modifying
  @Query(value = "UPDATE learning_summaries SET summary_status = 'GENERATING', "
      + "attempt_count = attempt_count + 1, lease_expires_at = :leaseUntil, "
      + "next_attempt_at = NULL WHERE session_id = :id AND ("
      + "(summary_status = 'QUEUED' AND next_attempt_at <= :now) OR "
      + "(summary_status = 'GENERATING' AND lease_expires_at <= :now "
      + "AND attempt_count < :maxAttempts))", nativeQuery = true)
  int claim(@Param("id") Long id, @Param("now") Instant now,
      @Param("leaseUntil") Instant leaseUntil, @Param("maxAttempts") short maxAttempts);

  /** 마지막 시도 중에 Worker가 사라진 작업은 다시 시도하지 않고 실패로 끝낸다. */
  @Modifying
  @Query(value = "UPDATE learning_summaries SET summary_status = 'FAILED', "
      + "lease_expires_at = NULL, next_attempt_at = NULL WHERE summary_status = 'GENERATING' "
      + "AND lease_expires_at <= :now AND attempt_count >= :maxAttempts", nativeQuery = true)
  int failAbandonedLeases(@Param("now") Instant now, @Param("maxAttempts") short maxAttempts);

  /**
   * 종료 리스너가 실행되지 못해(커밋 직후 서버 중단 등) 정리 행이 빠진 사용자 종료 세션을
   * 찾는다. ABANDONED는 자동 정리 대상이 아니므로 ENDED만 본다.
   */
  @Query("select s.id as sessionId, s.endedAt as endedAt from LearningSessionEntity s "
      + "where s.status = com.capstone.backend.session.persistence.SessionStatus.ENDED "
      + "and s.endedAt >= :from and s.endedAt <= :to and not exists ("
      + "select 1 from LearningSummaryEntity ls where ls.sessionId = s.id) order by s.endedAt")
  List<EndedWithoutSummary> findEndedWithoutSummary(@Param("from") Instant from,
      @Param("to") Instant to, Pageable page);

  /** 정리 행이 빠진 종료 세션의 식별자와 서버 종료 시각이다. */
  interface EndedWithoutSummary {
    /** 세션 ID다. */
    Long getSessionId();

    /** 서버가 확정한 종료 시각이다. */
    Instant getEndedAt();
  }

  /** 결과 저장 전에 점유 상태를 다시 확인하기 위해 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from LearningSummaryEntity s where s.sessionId = :id")
  Optional<LearningSummaryEntity> findForUpdate(@Param("id") Long id);
}
