package com.capstone.backend.intervention.service;

import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 동의 저장과 같은 transaction/사용자 잠금 아래 구간을 닫는다. */
@Component
@RequiredArgsConstructor
public class InterventionConsentListener {
  private final LearningSessionRepository sessions;
  private final InterventionPolicyService policy;

  /** 재동의 전에 중단 경계를 영속화하므로 heartbeat 사이의 철회도 유실되지 않는다. */
  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void withdrawn(RequiredConsentWithdrawal event) {
    sessions.findByUserIdAndStatusIn(event.userId(), List.of(SessionStatus.RUNNING,
        SessionStatus.PAUSED, SessionStatus.AUTO_PAUSED, SessionStatus.AWAITING_END_CONFIRMATION,
        SessionStatus.ENDING)).flatMap(value ->
            sessions.findOwnedForUpdate(value.getId(), event.userId()))
        .ifPresent(value -> policy.consentWithdrawn(value, event.withdrawnAt()));
  }
}
