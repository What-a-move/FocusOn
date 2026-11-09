package com.capstone.backend.intervention.service;

import com.capstone.backend.analysis.persistence.AnalysisRunEntity;
import com.capstone.backend.analysis.persistence.AnalysisRunRepository;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.security.TokenFamilyGuard;
import com.capstone.backend.intervention.persistence.InterventionEventEntity;
import com.capstone.backend.intervention.persistence.InterventionEventRepository;
import com.capstone.backend.intervention.persistence.InterventionPolicyRepository;
import com.capstone.backend.intervention.persistence.InterventionPolicyState;
import com.capstone.backend.session.dto.SessionDtos.SessionOptions;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.SessionStatus;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserConsentRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 호출자는 사용자→세션 잠금 순서로 최신 권한·세션을 확보한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class InterventionPolicyService {
  private final ForegroundPolicyContext contexts;
  private final InterventionPolicyRepository states;
  private final InterventionEventRepository events;
  private final AnalysisRunRepository runs;
  private final UserDeviceRepository devices;
  private final TokenFamilyRepository families;
  private final TokenFamilyGuard guard;
  private final UserConsentRepository consents;
  private final ObjectMapper json;

  /** 분석 예약의 processing과 이미 확정된 보호 결과를 구분한다. */
  public void prepared(LearningSessionEntity session, AnalysisRunEntity run, boolean current,
      boolean guarded, Instant now) {
    if (!current || run.getObservationContextId() == null) {
      return;
    }
    var binding = binding(session, now);
    if (binding.isEmpty()) {
      invalidate(session, now);
      return;
    }
    if (binding.get().deviceId() != run.getDevice().getId()) {
      return;
    }
    if (!contexts.contains(session.getId(), run.getId(), binding.get().context())) {
      invalidate(session, now);
      return;
    }
    var state = load(session, binding.get(), now);
    var timeline = read(state).pending(run.getId(), now);
    if (guarded || run.isOrderingUntrusted()) {
      timeline = timeline.interrupt(now);
    }
    state.transition(json.writeValueAsString(timeline.snapshot()));
    expirePending(session, now, guarded || run.isOrderingUntrusted());
  }

  /** 늦은 결과는 findCurrent로 다시 확인하며 다른 기기/context의 보호 상태를 풀지 않는다. */
  public void completed(LearningSessionEntity session, AnalysisRunEntity run, Instant now) {
    if (run.getObservationContextId() == null) {
      return;
    }
    var binding = binding(session, now);
    if (binding.isEmpty()) {
      invalidate(session, now);
      return;
    }
    var selected = binding.get();
    if (selected.deviceId() != run.getDevice().getId()
        || !contexts.contains(session.getId(), run.getId(), selected.context())
        || run.isOrderingUntrusted()
        || !run.isCurrentEligible() || runs.findCurrent(session.getId(), selected.deviceId(),
            Long.parseLong(selected.context()))
            .map(current -> !current.getId().equals(run.getId())).orElse(true)) {
      return;
    }
    var state = load(session, selected, now);
    var timeline = read(state);
    if (!Objects.equals(timeline.snapshot().pendingRun(), run.getId())) {
      // 철회/보호/실행 전이가 이미 지운 pending을 이전 분석 완료로 복원하지 않는다.
      // 유효 관측은 prepare와 pending 상태를 같은 transaction에서 저장한다.
      return;
    }
    timeline = timeline.complete(run.getId(), run.effectiveRelation(), run.getContentSource(),
        run.getReceivedAt(), now);
    state.transition(json.writeValueAsString(timeline.snapshot()));
    evaluate(session, selected, state, timeline, now);
  }

  /** 신호 자체는 관측이 아니므로 최신 확정 상태의 경과 시간만 평가한다. */
  public void heartbeat(LearningSessionEntity session, Instant now) {
    var binding = binding(session, now);
    if (binding.isEmpty()) {
      invalidate(session, now);
      return;
    }
    var state = states.findById(session.getId()).orElse(null);
    if (state == null) {
      return;
    }
    var timeline = read(state);
    if (!Objects.equals(timeline.snapshot().runningSince(), session.getLastResumedAt())
        || !timeline.snapshot().context().equals(binding.get().context())) {
      invalidate(session, now);
      return;
    }
    var current = runs.findCurrent(session.getId(), binding.get().deviceId(),
        Long.parseLong(binding.get().context())).orElse(null);
    if (current != null && current.isOrderingUntrusted()) {
      invalidate(session, current.getReceivedAt());
      return;
    }
    if (current == null || !Objects.equals(current.getId(), timeline.snapshot().latestRun())) {
      return;
    }
    evaluate(session, binding.get(), state, timeline, now);
  }

  /** 실제 앱이 아직 보내지 않는 context를 기기별 번호/시각으로 만들어내지 않는다. */
  public Optional<ForegroundPolicyContext.Binding> binding(LearningSessionEntity session,
      Instant now) {
    if (session.getStatus() != SessionStatus.RUNNING || session.getLastResumedAt() == null) {
      return Optional.empty();
    }
    for (var consent : new ConsentType[] {ConsentType.TERMS_OF_SERVICE,
        ConsentType.PRIVACY_POLICY, ConsentType.SCREEN_ANALYSIS}) {
      if (!consents.findByUserIdAndConsentType(session.getUser().getId(), consent)
          .map(value -> value.isGranted()).orElse(false)) {
        return Optional.empty();
      }
    }
    return contexts.resolve(session.getId()).filter(value -> value.context() != null
        && !value.context().isBlank() && value.context().length() <= 128
        && value.validSince() != null && !value.validSince().isAfter(now)
        && value.validUntil() != null && value.validUntil().isAfter(now))
        .filter(value -> activeDevice(session, value.deviceId(), now));
  }

  private boolean activeDevice(LearningSessionEntity session, long deviceId, Instant now) {
    var device = devices.findById(deviceId).orElse(null);
    if (device == null || !device.getUser().getId().equals(session.getUser().getId())
        || device.getStatus() != UserDeviceEntity.Status.ACTIVE) {
      return false;
    }
    var runner = session.getRunnerDevice();
    boolean linked = device.getId().equals(runner.getId())
        || (device.getDeviceType() == UserDeviceEntity.DeviceType.CHROME_EXTENSION
            && device.getLinkedDesktopDevice() != null
            && device.getLinkedDesktopDevice().getId().equals(runner.getId()));
    return linked && guard.hasActiveLinkedDesktop(device, session.getUser().getId(), now)
        && families.findByDeviceAndStatus(deviceId, TokenFamilyEntity.Status.ACTIVE)
            .filter(family -> guard.isActive(family, now)).isPresent();
  }

  private InterventionPolicyState load(LearningSessionEntity session,
      ForegroundPolicyContext.Binding context, Instant now) {
    var state = states.findById(session.getId()).orElseGet(() -> states.save(
        InterventionPolicyState.create(session.getId(), json.writeValueAsString(
            OffTaskTimeline.empty(session.getLastResumedAt(), context.context(), now)
                .snapshot()))));
    var timeline = read(state);
    if (!Objects.equals(timeline.snapshot().runningSince(), session.getLastResumedAt())) {
      state.transition(json.writeValueAsString(
          OffTaskTimeline.empty(session.getLastResumedAt(), context.context(), now).snapshot()));
    } else if (!timeline.snapshot().context().equals(context.context())) {
      state.transition(json.writeValueAsString(
          timeline.switchContext(context.context(), now).snapshot()));
    }
    return state;
  }

  private OffTaskTimeline read(InterventionPolicyState state) {
    return new OffTaskTimeline(
        json.readValue(state.getTimelineJson(), OffTaskTimeline.Snapshot.class));
  }

  /** 동의 철회 저장 transaction에서 즉시 닫아 재동의가 중단 이력을 숨기지 못하게 한다. */
  public void consentWithdrawn(LearningSessionEntity session, Instant now) {
    invalidate(session, now);
  }

  private void invalidate(LearningSessionEntity session, Instant now) {
    states.findById(session.getId()).ifPresent(state -> {
      var timeline = read(state);
      Instant cutoff = contexts.expiry(timeline.snapshot().context())
          .filter(at -> at.isBefore(now)).orElse(now);
      state.transition(json.writeValueAsString(timeline.interrupt(cutoff).snapshot()));
    });
    expirePending(session, now, true);
  }

  private void expirePending(LearningSessionEntity session, Instant now, boolean blocked) {
    events.findBySessionIdOrderByCreatedAtAscIdAsc(session.getId()).stream()
        .filter(event -> blocked || !now.isBefore(event.getExpiresAt()))
        .forEach(InterventionEventEntity::expire);
    events.flush();
  }

  private void evaluate(LearningSessionEntity session, ForegroundPolicyContext.Binding context,
      InterventionPolicyState state, OffTaskTimeline timeline, Instant now) {
    expirePending(session, now, timeline.snapshot().openEpisode() == null);
    if (!context.deliveryContractReady() || context.deliveryUnconfirmed()
        || timeline.snapshot().pendingRun() != null) {
      return;
    }
    var history = events.findBySessionIdOrderByCreatedAtAscIdAsc(session.getId());
    // NOT_SENT도 TTL 안에는 같은 후보를 재시도하며 별도 pending 후보를 생성하지 않는다.
    if (history.stream().anyMatch(event -> event.getStatus()
        == com.capstone.backend.intervention.dto.InterventionDtos.Status.PENDING
        && event.getDeliveryConfirmedAt() == null)) {
      return;
    }
    // receipt 미확인 이벤트는 ack/만료 뒤에도 재발송을 막는다. ack는 receipt가 아니다.
    if (history.stream().anyMatch(event -> event.getDeliveryConfirmedAt() == null
        && !contexts.definitelyNotSent(event.getId()))) {
      return;
    }
    var options = json.readValue(session.getSessionOptionsSnapshotJson(), SessionOptions.class);
    if (timeline.evaluate(session.getInterventionPolicyVersion(), options.interventionLevel(),
        now, context.cooldownUntil()).candidate()) {
      boolean duplicate = history.stream()
          .anyMatch(event -> event.getCause() == state.getRevision());
      if (!duplicate) {
        var event = InterventionEventEntity.offTask(session.getId(), state.getRevision(),
            json.writeValueAsString(options.notificationChannels()),
            session.getInterventionPolicyVersion(), session.getLastResumedAt(), now);
        event.bindContext(InterventionAccess.id(context.context(),
            com.capstone.backend.common.api.ApiErrorCode.VALIDATION_ERROR));
        events.saveAndFlush(event);
      }
    }
  }
}
