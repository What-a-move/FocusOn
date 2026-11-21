package com.capstone.backend.report.persistence;

import com.capstone.backend.report.dto.LearningSummaryResponse.Status;
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

/** 생성자가 저장한 세션별 단일 정리를 읽고, 작업 점유·완료를 조건부 갱신으로 처리한다. */
public interface LearningSummaryRepository extends JpaRepository<LearningSummaryEntity, Long> {
  /** 입력 마감과 재시도 시각이 지난 대기 작업, 또는 lease가 끝난 생성 중 작업을 찾는다. */
  @Query("select s.sessionId from LearningSummaryEntity s where "
      + "(s.status = :queued and s.nextAttemptAt <= :now) "
      + "or (s.status = :generating and s.leaseUntil < :now) order by s.nextAttemptAt")
  List<Long> findDue(@Param("queued") Status queued, @Param("generating") Status generating,
      @Param("now") Instant now, Pageable page);

  /**
   * 한 worker만 작업을 점유한다. 성공하면 시도 횟수를 1 올리며, 이 값이 이후 완료 갱신의
   * fencing token이 된다. lease가 끝난 생성 중 작업도 같은 방식으로 다시 점유한다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update LearningSummaryEntity s set s.status = :generating, s.leaseUntil = :lease, "
      + "s.attemptCount = s.attemptCount + 1, s.updatedAt = :now where s.sessionId = :id "
      + "and s.attemptCount < 3 and ((s.status = :queued and s.nextAttemptAt <= :now) "
      + "or (s.status = :generating and s.leaseUntil < :now))")
  int claim(@Param("id") long id, @Param("queued") Status queued,
      @Param("generating") Status generating, @Param("now") Instant now,
      @Param("lease") Instant lease);

  /** READY 저장은 JSON 필드를 엔티티로 다루기 위해 행을 잠근 뒤 시도 번호를 다시 확인한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from LearningSummaryEntity s where s.sessionId = :id")
  Optional<LearningSummaryEntity> findForUpdate(@Param("id") long id);

  /** 마지막 시도의 lease가 끝난 생성 중 작업은 다시 점유하지 않고 FAILED로 닫는다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update LearningSummaryEntity s set s.status = :failed, s.errorCode = 'LEASE_EXPIRED', "
      + "s.leaseUntil = null, s.updatedAt = :now where s.status = :generating "
      + "and s.leaseUntil < :now and s.attemptCount >= 3")
  int failExhausted(@Param("generating") Status generating, @Param("failed") Status failed,
      @Param("now") Instant now);

  /** 점유한 시도를 내용 없는 상태(근거 부족·실패·재대기)로 끝낸다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update LearningSummaryEntity s set s.status = :next, s.nextAttemptAt = :retryAt, "
      + "s.errorCode = :error, s.evidenceCount = :evidence, s.updatedAt = :now, "
      + "s.leaseUntil = null where s.sessionId = :id and s.status = :generating "
      + "and s.attemptCount = :attempt")
  int completeWithoutContent(@Param("id") long id, @Param("attempt") short attempt,
      @Param("generating") Status generating, @Param("next") Status next,
      @Param("retryAt") Instant retryAt, @Param("error") String error,
      @Param("evidence") Integer evidence, @Param("now") Instant now);
}
