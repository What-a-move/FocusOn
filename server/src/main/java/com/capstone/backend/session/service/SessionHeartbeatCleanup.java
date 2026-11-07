package com.capstone.backend.session.service;

import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 무신호 세션을 소유자 잠금과 조건부 갱신으로 정리한다. */
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SessionHeartbeatCleanup {
  private static final Duration ABANDONED_AFTER = Duration.ofSeconds(180);
  private static final int BATCH_SIZE = 100;
  private final LearningSessionRepository sessions;
  private final UserRepository users;
  private final TransactionTemplate transactions;

  /** 정상 수신된 신호와 종료가 경합해도 최신 생존 시각을 다시 확인한다. */
  @Scheduled(fixedDelayString = "${focuson.session.heartbeat-cleanup-delay-ms:10000}")
  public void cleanup() {
    Instant cutoff = Instant.now().minus(ABANDONED_AFTER);
    var candidates = sessions.findExpiredCandidates(cutoff, PageRequest.of(0, BATCH_SIZE));
    for (Long id : candidates) {
      transactions.executeWithoutResult(status -> sessions.findById(id).ifPresent(session -> {
        users.findByIdForUpdate(session.getUser().getId());
        Instant now = Instant.now();
        sessions.abandonIfExpired(id, now.minus(ABANDONED_AFTER), now);
      }));
    }
  }
}
