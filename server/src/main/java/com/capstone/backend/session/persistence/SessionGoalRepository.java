package com.capstone.backend.session.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 세션에 고정된 목표 사본을 순서대로 읽는다. */
public interface SessionGoalRepository
    extends JpaRepository<SessionGoalEntity, SessionGoalEntity.Key> {
  /** 세션의 목표 사본을 position 순서로 돌려준다. */
  List<SessionGoalEntity> findByIdSessionIdOrderByIdPositionAsc(Long sessionId);

  /** 세션의 할 일 상한을 검사할 때 사용한다. */
  long countByIdSessionId(Long sessionId);

  /** 추가한 목표도 활성 세션 중에는 수정·삭제하지 못하게 보호한다. */
  @Query("select count(g) > 0 from SessionGoalEntity g, LearningSessionEntity s "
      + "where g.id.sessionId = s.id and s.user.id = :userId and g.goalId = :goalId "
      + "and s.status in :statuses")
  boolean referencedByActiveSession(@Param("userId") Long userId, @Param("goalId") Long goalId,
      @Param("statuses") Collection<SessionStatus> statuses);
}
