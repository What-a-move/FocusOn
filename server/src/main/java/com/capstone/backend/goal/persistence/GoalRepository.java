package com.capstone.backend.goal.persistence;

import com.capstone.backend.goal.persistence.GoalEntity.TodoStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사용자의 삭제되지 않은 목표를 조회한다. */
public interface GoalRepository extends JpaRepository<GoalEntity, Long> {
  /** 사용자의 삭제되지 않은 목표만 찾는다. */
  Optional<GoalEntity> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

  /** 할 일 목록 한 페이지다. 최신 목표부터, 상태 필터는 null이면 전체다. */
  @Query("""
      SELECT g FROM GoalEntity g
      WHERE g.user.id = :userId AND g.deletedAt IS NULL
        AND g.status = com.capstone.backend.goal.persistence.GoalEntity.Status.ACTIVE
        AND (:todoStatus IS NULL OR g.todoStatus = :todoStatus)
        AND g.id < :idBefore
      ORDER BY g.id DESC
      """)
  List<GoalEntity> findTodoPage(@Param("userId") Long userId,
      @Param("todoStatus") TodoStatus todoStatus, @Param("idBefore") long idBefore,
      Pageable page);
}
