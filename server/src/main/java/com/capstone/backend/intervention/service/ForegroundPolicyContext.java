package com.capstone.backend.intervention.service;

import com.capstone.backend.analysis.persistence.AnalysisRunRepository;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.intervention.dto.InterventionDtos.DispatchStatus;
import com.capstone.backend.intervention.dto.ObservationContextDtos.Observer;
import com.capstone.backend.intervention.persistence.InterventionDeliveryRepository;
import com.capstone.backend.intervention.persistence.InterventionEventRepository;
import com.capstone.backend.intervention.persistence.ObservationContextEntity;
import com.capstone.backend.intervention.persistence.ObservationContextRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Desktop의 명시적 위임만 선택하며 기기별 시각/번호로 전경을 추정하지 않는다. */
@Component
@RequiredArgsConstructor
public class ForegroundPolicyContext {
  private final ObservationContextRepository contexts;
  private final LearningSessionRepository sessions;
  private final AnalysisRunRepository runs;
  private final InterventionEventRepository events;
  private final InterventionDeliveryRepository deliveries;

  /** 만료 경계를 정책에서 닫을 수 있도록 lease 끝 시각도 반환한다. */
  public Optional<Binding> resolve(long sessionId) {
    var session = sessions.findById(sessionId).orElse(null);
    if (session == null) {
      return Optional.empty();
    }
    return contexts.findFirstBySessionIdOrderByForegroundSequenceDesc(sessionId)
        .filter(value -> value.getObserver() != Observer.NONE
            && Objects.equals(value.getRunningSince(), session.getLastResumedAt()))
        .map(value -> {
          var history = events.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId);
          Instant cooldown = history.stream().map(event -> event.getDeliveryConfirmedAt())
              .filter(Objects::nonNull).max(Instant::compareTo)
              .map(at -> at.plusSeconds(300)).orElse(null);
          boolean unknown = history.stream().anyMatch(event ->
              event.getDeliveryConfirmedAt() == null && !definitelyNotSent(event.getId()));
          return new Binding(value.getObserverDeviceId(), value.getId().toString(),
              value.getCreatedAt(), true, cooldown, unknown, value.getValidUntil());
        });
  }

  /** run에 저장된 검증 완료 참조만 인정한다. */
  public boolean contains(long sessionId, long runId, String context) {
    return runs.findById(runId).filter(run -> run.getSession().getId().equals(sessionId)
        && run.getObservationContextId() != null
        && run.getObservationContextId().toString().equals(context)).isPresent();
  }

  /** 기존 context 없는 분석은 이력 호환만 유지한다. */
  public Long requireForRun(LearningSessionEntity session, long deviceId, String raw,
      Instant now) {
    if (raw == null) {
      return null;
    }
    long id = InterventionAccess.id(raw, ApiErrorCode.VALIDATION_ERROR);
    var value = contexts.findFirstBySessionIdOrderByForegroundSequenceDesc(session.getId())
        .filter(item -> item.getId() == id && item.getObserver() != Observer.NONE
            && Objects.equals(item.getObserverDeviceId(), deviceId)
            && current(session, item, now))
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_STATE_INVALID));
    return value.getId();
  }

  /** 유효 RUNNING heartbeat commit 안에서만 호출한다. 잘못된 context로 시간을 실패시키지 않는다. */
  public void renew(LearningSessionEntity session, String raw, Instant now) {
    if (raw == null) {
      return;
    }
    contexts.findFirstBySessionIdOrderByForegroundSequenceDesc(session.getId())
        .filter(value -> value.getId().toString().equals(raw) && current(session, value, now))
        .ifPresent(value -> value.renew(now));
  }

  /** 늦게 발견한 lease 만료 뒤의 시간을 소급 합산하지 않는다. */
  public Optional<Instant> expiry(String context) {
    try {
      return contexts.findById(Long.parseLong(context)).map(value -> value.getValidUntil());
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  /** UNKNOWN을 후속 NOT_SENT로 덮지 않으며 receipt 없음도 미확인이다. */
  public boolean definitelyNotSent(long interventionId) {
    var history = deliveries.findByInterventionIdOrderByIdAsc(interventionId);
    return !history.isEmpty() && history.stream()
        .allMatch(value -> value.getDispatchStatus() == DispatchStatus.NOT_SENT);
  }

  /** 실행 세대와 lease를 서버 시각으로만 판정한다. */
  public boolean current(LearningSessionEntity session, ObservationContextEntity context,
      Instant now) {
    return session.getStatus()
        == com.capstone.backend.session.persistence.SessionStatus.RUNNING
        && context.getRunnerDeviceId().equals(session.getRunnerDevice().getId())
        && Objects.equals(context.getRunningSince(), session.getLastResumedAt())
        && context.getValidUntil().isAfter(now) && !context.getCreatedAt().isAfter(now);
  }

  /** 공개 DTO가 아닌 내부 검증 완료 입력이다. */
  public record Binding(long deviceId, String context, Instant validSince,
      boolean deliveryContractReady, Instant cooldownUntil, boolean deliveryUnconfirmed,
      Instant validUntil) {
    /** 기존 합성 정책 입력은 무제한 lease를 명시하는 테스트 seam이다. */
    public Binding(long deviceId, String context, Instant validSince,
        boolean deliveryContractReady, Instant cooldownUntil, boolean deliveryUnconfirmed) {
      this(deviceId, context, validSince, deliveryContractReady, cooldownUntil,
          deliveryUnconfirmed, Instant.MAX);
    }
  }
}
