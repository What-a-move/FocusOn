package com.capstone.backend.intervention.service;

import com.capstone.backend.common.api.SafeApiLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 정책 장애가 이미 확정된 heartbeat 생존 기록을 rollback하지 않게 한다. */
@Component
@RequiredArgsConstructor
public class HeartbeatPolicyListener {
  private final HeartbeatPolicyEvaluator evaluator;

  /** 실패한 시간 평가는 다음 유효 heartbeat/관측에서 최신 상태로 다시 수행한다. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void committed(HeartbeatPolicyTrigger event) {
    long started = System.nanoTime();
    try {
      evaluator.evaluate(event.userId(), event.sessionId());
    } catch (RuntimeException exception) {
      SafeApiLog.analysisEvent(SafeApiLog.AnalysisEvent.STORE_FAIL, started);
    }
  }
}
