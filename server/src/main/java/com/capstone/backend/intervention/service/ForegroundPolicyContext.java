package com.capstone.backend.intervention.service;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 공개 전경/전송 계약이 합의되기 전에는 서버가 담당 기기나 전송을 추측하지 않는다. */
@Component
public class ForegroundPolicyContext {
  /** 실제 adapter 연결 전에는 어떤 관측도 production 개입 근거로 선택하지 않는다. */
  public Optional<Binding> resolve(long sessionId) {
    return Optional.empty();
  }

  /** 관측이 현재 전경 context에 속한다는 명시적 연결 근거가 있어야 한다. */
  public boolean contains(long sessionId, long runId, String context) {
    return false;
  }

  /** 공개 DTO가 아닌 내부 검증 완료 입력이다. */
  public record Binding(long deviceId, String context, Instant validSince,
      boolean deliveryContractReady, Instant cooldownUntil, boolean deliveryUnconfirmed) {}
}
