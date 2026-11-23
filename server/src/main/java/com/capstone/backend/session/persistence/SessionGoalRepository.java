package com.capstone.backend.session.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 세션에 고정된 목표 사본을 순서대로 읽는다. */
public interface SessionGoalRepository
    extends JpaRepository<SessionGoalEntity, SessionGoalEntity.Key> {
  /** 세션의 목표 사본을 position 순서로 돌려준다. */
  List<SessionGoalEntity> findByIdSessionIdOrderByIdPositionAsc(Long sessionId);
}
