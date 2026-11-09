package com.capstone.backend.intervention.service;

import com.capstone.backend.common.api.SafeApiLog;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 정책 장애가 connection 정리가 끝난 heartbeat 생존 기록을 rollback하지 않게 한다. */
@Component
@RequiredArgsConstructor
public class HeartbeatPolicyListener {
  private final HeartbeatPolicyEvaluator evaluator;

  /** 실패한 시간 평가는 다음 유효 heartbeat/관측에서 최신 상태로 다시 수행한다. */
  @EventListener
  public void committed(HeartbeatPolicyTrigger event) {
    long started = System.nanoTime();
    try {
      evaluator.evaluate(event.userId(), event.sessionId());
    } catch (RuntimeException exception) {
      SafeApiLog.analysisEvent(SafeApiLog.AnalysisEvent.STORE_FAIL, started);
    }
  }
}
