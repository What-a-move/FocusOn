package com.capstone.backend.report.service;

import com.capstone.backend.common.api.SafeApiLog;
import com.capstone.backend.report.persistence.LearningSummaryRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 주기적으로 빠진 대기열을 복구하고 실행 시각이 된 학습 정리 작업을 처리한다. */
@Component
@RequiredArgsConstructor
public class LearningSummaryWorker {
  private static final int BATCH_SIZE = 10;
  /** 종료 리스너가 막 실행 중인 세션과 겹치지 않도록 이만큼 지난 종료만 복구한다. */
  private static final Duration RECOVERY_DELAY = Duration.ofMinutes(1);
  /** 복구 조회 범위다. 더 오래된 종료는 기능 도입 전 기록으로 보고 정리를 만들지 않는다. */
  private static final Duration RECOVERY_WINDOW = Duration.ofDays(1);
  private final LearningSummaryRepository summaries;
  private final LearningSummaryQueue queue;
  private final LearningSummaryGenerator generator;
  private final TransactionTemplate transactions;

  /** 한 주기에 최대 BATCH_SIZE개를 처리한다. 다중 인스턴스여도 claim이 중복 생성을 막는다. */
  @Scheduled(fixedDelayString = "${focuson.learning-summary.worker-delay-ms:5000}",
      initialDelayString = "${focuson.learning-summary.worker-initial-delay-ms:30000}")
  public void run() {
    Instant now = Instant.now();
    recoverMissingQueues(now);
    try {
      transactions.executeWithoutResult(status ->
          summaries.failAbandonedLeases(now, LearningSummaryGenerator.MAX_ATTEMPTS));
    } catch (RuntimeException exception) {
      SafeApiLog.learningSummaryFailure(exception);
    }
    for (Long sessionId : summaries.findClaimable(now, LearningSummaryGenerator.MAX_ATTEMPTS,
        PageRequest.of(0, BATCH_SIZE))) {
      try {
        generator.process(sessionId);
      } catch (RuntimeException exception) {
        // 점유한 작업은 lease 만료 뒤 다시 시도되므로 다음 작업을 계속 처리한다.
        SafeApiLog.learningSummaryFailure(exception);
      }
    }
  }

  private void recoverMissingQueues(Instant now) {
    for (var ended : summaries.findEndedWithoutSummary(now.minus(RECOVERY_WINDOW),
        now.minus(RECOVERY_DELAY), PageRequest.of(0, BATCH_SIZE))) {
      try {
        queue.enqueue(ended.getSessionId(), ended.getEndedAt());
      } catch (RuntimeException exception) {
        SafeApiLog.learningSummaryFailure(exception);
      }
    }
  }
}
