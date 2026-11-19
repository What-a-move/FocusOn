package com.capstone.backend.report.service;

import com.capstone.backend.common.api.SafeApiLog;
import com.capstone.backend.report.persistence.LearningSummaryEntity;
import com.capstone.backend.report.persistence.LearningSummaryRepository;
import com.capstone.backend.session.service.SessionEnded;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** 사용자 종료된 세션마다 학습 정리 행 하나를 QUEUED로 만든다. */
@Component
public class LearningSummaryQueue {
  private final LearningSummaryRepository summaries;
  private final TransactionTemplate newTransaction;

  /** 종료 트랜잭션이 끝난 뒤 실행되므로 항상 새 트랜잭션을 사용한다. */
  public LearningSummaryQueue(LearningSummaryRepository summaries,
      PlatformTransactionManager transactionManager) {
    this.summaries = summaries;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * 종료가 커밋된 뒤에만 대기열을 만든다. 여기서 실패해도 종료는 유지되고 Worker의 복구 조회가
   * 빠진 행을 다시 만든다.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void sessionEnded(SessionEnded event) {
    try {
      enqueue(event.sessionId(), event.endedAt());
    } catch (RuntimeException exception) {
      SafeApiLog.learningSummaryFailure(exception);
    }
  }

  /**
   * 이미 행이 있으면 아무것도 하지 않는다. 세션당 정리는 PK로 하나만 존재하므로 동시에 만들어도
   * 한쪽은 제약 위반으로 실패하고 행은 하나만 남는다.
   */
  void enqueue(long sessionId, Instant endedAt) {
    newTransaction.executeWithoutResult(status -> {
      if (!summaries.existsById(sessionId)) {
        summaries.saveAndFlush(LearningSummaryEntity.queued(sessionId, endedAt));
      }
    });
  }
}
