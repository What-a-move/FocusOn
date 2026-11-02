package com.capstone.backend.session.persistence;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자별 학습 세션에 접근한다. */
public interface LearningSessionRepository extends JpaRepository<LearningSessionEntity, Long> {
  /** 현재 사용자의 활성 상태 세션을 찾는다. */
  Optional<LearningSessionEntity> findByUserIdAndStatusIn(Long userId,
      Collection<SessionStatus> statuses);

  /** 특정 목표를 참조하는 활성 세션이 있는지 확인한다. */
  boolean existsByUserIdAndGoalIdAndStatusIn(Long userId, Long goalId,
      Collection<SessionStatus> statuses);
}
