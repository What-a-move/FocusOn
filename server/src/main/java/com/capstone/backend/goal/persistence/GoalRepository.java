package com.capstone.backend.goal.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자의 삭제되지 않은 목표를 조회한다. */
public interface GoalRepository extends JpaRepository<GoalEntity, Long> {
  /** 사용자의 삭제되지 않은 목표만 찾는다. */
  Optional<GoalEntity> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
