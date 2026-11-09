package com.capstone.backend.intervention.service;

import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** heartbeat bulk update의 managed snapshot을 사용하지 않는 평가 transaction이다. */
@Service
@RequiredArgsConstructor
public class HeartbeatPolicyEvaluator {
  private final EntityManager entityManager;
  private final UserRepository users;
  private final LearningSessionRepository sessions;
  private final InterventionPolicyService policy;

  /** 사용자→세션 잠금을 다시 얻고 최신 실행 상태를 재검증한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void evaluate(long userId, long sessionId) {
    entityManager.clear();
    if (users.findByIdForUpdate(userId).isEmpty()) {
      return;
    }
    sessions.findOwnedForUpdate(sessionId, userId)
        .ifPresent(session -> policy.heartbeat(session, Instant.now()));
  }
}
