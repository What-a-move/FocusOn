package com.capstone.backend.session.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사용자별 학습 세션에 접근한다. */
public interface LearningSessionRepository extends JpaRepository<LearningSessionEntity, Long> {
  /** 고정된 학습 날짜와 ID 커서로 본인의 기록을 조회한다. */
  @Query("select s from LearningSessionEntity s where s.user.id = :userId "
      + "and s.studyDate >= :from and s.studyDate <= :to and s.id < :cursor order by s.id desc")
  java.util.List<LearningSessionEntity> findHistory(@Param("userId") Long userId,
      @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to,
      @Param("cursor") long cursor, Pageable page);

  /** 현재 사용자의 활성 상태 세션을 찾는다. */
  Optional<LearningSessionEntity> findByUserIdAndStatusIn(Long userId,
      Collection<SessionStatus> statuses);

  /** 특정 목표를 참조하는 활성 세션이 있는지 확인한다. */
  boolean existsByUserIdAndGoalIdAndStatusIn(Long userId, Long goalId,
      Collection<SessionStatus> statuses);

  /** 현재 사용자에게 속한 세션만 조회한다. */
  Optional<LearningSessionEntity> findByIdAndUserId(Long id, Long userId);

  /** 신호와 자동 종료가 같은 세션을 동시에 확정하지 않도록 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from LearningSessionEntity s where s.id = :id and s.user.id = :userId")
  Optional<LearningSessionEntity> findOwnedForUpdate(@Param("id") Long id,
      @Param("userId") Long userId);

  /** 일반 신호는 명령 버전을 변경하지 않고 상태 전이 때만 버전을 증가한다. */
  @Modifying(flushAutomatically = true)
  @Query(value = "UPDATE learning_sessions SET last_heartbeat_at = :received, "
      + "last_heartbeat_observed_at = :observed, accumulated_active_ms = :active, "
      + "status = CASE WHEN :reached THEN 'AWAITING_END_CONFIRMATION' ELSE status END, "
      + "version = version + CASE WHEN :reached THEN 1 ELSE 0 END "
      + "WHERE id = :id AND status = 'RUNNING'", nativeQuery = true)
  int recordHeartbeat(@Param("id") Long id, @Param("received") Instant received,
      @Param("observed") Instant observed, @Param("active") long active,
      @Param("reached") boolean reached);

  /** 현재 실행 구간의 마지막 생존 기준을 기준으로 정리 후보를 찾는다. */
  @Query("select s.id from LearningSessionEntity s where s.status = "
      + "com.capstone.backend.session.persistence.SessionStatus.RUNNING "
      + "and s.id > :afterId and s.lastResumedAt <= :cutoff and "
      + "(s.lastHeartbeatAt is null or s.lastHeartbeatAt <= :cutoff) order by s.id")
  java.util.List<Long> findExpiredCandidates(@Param("cutoff") Instant cutoff,
      @Param("afterId") long afterId, Pageable page);

  /** 최신 신호가 이미 반영됐다면 조건부 갱신이 실패한다. */
  @Modifying
  @Query(value = "UPDATE learning_sessions SET status = 'ABANDONED', "
      + "end_reason = 'HEARTBEAT_TIMEOUT', ended_at = :now, version = version + 1 "
      + "WHERE id = :id AND status = 'RUNNING' AND last_resumed_at <= :cutoff "
      + "AND (last_heartbeat_at IS NULL OR last_heartbeat_at <= :cutoff)", nativeQuery = true)
  int abandonIfExpired(@Param("id") Long id, @Param("cutoff") Instant cutoff,
      @Param("now") Instant now);
}
