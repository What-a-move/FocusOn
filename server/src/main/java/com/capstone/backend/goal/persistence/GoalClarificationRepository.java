package com.capstone.backend.goal.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사용자 소유 대화 이력과 동시 변경을 관리한다. */
public interface GoalClarificationRepository extends JpaRepository<GoalClarificationSession, Long> {
  /** 같은 대화의 상태 확정을 직렬화한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from GoalClarificationSession s where s.id = :id and s.user.id = :userId")
  Optional<GoalClarificationSession> findOwnedForUpdate(@Param("id") Long id,
      @Param("userId") Long userId);
}
