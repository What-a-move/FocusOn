package com.capstone.backend.intervention.service;

import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.security.TokenFamilyGuard;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.intervention.dto.ObservationContextDtos.Item;
import com.capstone.backend.intervention.dto.ObservationContextDtos.Observer;
import com.capstone.backend.intervention.dto.ObservationContextDtos.Put;
import com.capstone.backend.intervention.persistence.InterventionEventEntity;
import com.capstone.backend.intervention.persistence.InterventionEventRepository;
import com.capstone.backend.intervention.persistence.InterventionPolicyRepository;
import com.capstone.backend.intervention.persistence.ObservationContextEntity;
import com.capstone.backend.intervention.persistence.ObservationContextRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 명시적 전경 변경과 lease 읽기만 담당하며 세션 명령을 재구현하지 않는다. */
@Service
@RequiredArgsConstructor
public class ObservationContextService {
  private final InterventionAccess access;
  private final ObservationContextRepository contexts;
  private final ForegroundPolicyContext foreground;
  private final UserDeviceRepository devices;
  private final TokenFamilyRepository families;
  private final TokenFamilyGuard guard;
  private final InterventionPolicyRepository states;
  private final InterventionEventRepository events;
  private final ObjectMapper json;

  /** 같은 nonce는 최초 context를 보존하고 lease를 연장하지 않는다. */
  @Transactional
  public Item put(FocusOnPrincipal principal, String deviceId, String rawSession, Put request) {
    DeviceHeaderValidator.require(deviceId, principal);
    var device = access.authenticate(principal, false);
    var session = access.owned(principal, device,
        InterventionAccess.id(rawSession, ApiErrorCode.SESSION_NOT_FOUND));
    if (session.getStatus() != SessionStatus.RUNNING || session.getLastResumedAt() == null) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    UUID nonce = UUID.fromString(request.clientContextId());
    var previous = contexts.findBySessionIdAndClientContextId(session.getId(), nonce)
        .orElse(null);
    if (previous != null) {
      if (previous.getForegroundSequence() != request.foregroundSequence().longValueExact()
          || previous.getObserver() != request.observer()) {
        throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
      }
      return item(previous, session, now);
    }
    var latest = contexts.findFirstBySessionIdOrderByForegroundSequenceDesc(session.getId());
    if (latest.filter(value -> value.getForegroundSequence()
        >= request.foregroundSequence().longValueExact())
        .isPresent()) {
      throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
    }
    Long observer = switch (request.observer()) {
      case DESKTOP -> device.getId();
      case NONE -> null;
      case EXTENSION -> linkedExtension(principal, device.getId(), now);
    };
    var saved = contexts.saveAndFlush(ObservationContextEntity.create(session.getId(),
        device.getId(), observer, request.observer(), nonce,
        request.foregroundSequence().longValueExact(),
        session.getLastResumedAt(), now));
    states.findById(session.getId()).ifPresent(state -> {
      var timeline = new OffTaskTimeline(
          json.readValue(state.getTimelineJson(), OffTaskTimeline.Snapshot.class));
      Instant cutoff = foreground.expiry(timeline.snapshot().context())
          .filter(at -> at.isBefore(now)).orElse(now);
      state.transition(json.writeValueAsString(timeline.interrupt(cutoff)
          .switchContext(saved.getId().toString(), now).snapshot()));
    });
    events.findBySessionIdOrderByCreatedAtAscIdAsc(session.getId())
        .forEach(InterventionEventEntity::expire);
    return item(saved, session, now);
  }

  /** 타인의 기기 정보는 숨기며 조회는 lease keepalive가 아니다. */
  @Transactional
  public Optional<Item> get(FocusOnPrincipal principal, String deviceId, String rawSession) {
    DeviceHeaderValidator.require(deviceId, principal);
    var device = access.authenticate(principal, true);
    var session = access.owned(principal, device,
        InterventionAccess.id(rawSession, ApiErrorCode.SESSION_NOT_FOUND));
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    return contexts.findFirstBySessionIdOrderByForegroundSequenceDesc(session.getId())
        .filter(value -> foreground.current(session, value, now))
        .filter(value -> value.getObserver() == Observer.NONE
            || available(value.getObserverDeviceId(), principal.userId(), now))
        .map(value -> item(value, session, now));
  }

  private Long linkedExtension(FocusOnPrincipal principal, long runnerId, Instant now) {
    var linked = devices.findByUserIdAndLinkedDesktopDeviceIdAndDeviceType(principal.userId(),
        runnerId, UserDeviceEntity.DeviceType.CHROME_EXTENSION).stream()
        .filter(value -> available(value.getId(), principal.userId(), now)).toList();
    if (linked.size() != 1) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    return linked.getFirst().getId();
  }

  private boolean available(long id, long userId, Instant now) {
    return families.findByDeviceAndStatus(id, TokenFamilyEntity.Status.ACTIVE)
        .filter(value -> value.getUser().getId().equals(userId) && guard.isActive(value, now)
            && guard.hasActiveLinkedDesktop(value.getDevice(), userId, now)).isPresent();
  }

  private Item item(ObservationContextEntity value, LearningSessionEntity session, Instant now) {
    long remaining = Objects.equals(value.getRunningSince(), session.getLastResumedAt())
        ? Math.max(0, Duration.between(now, value.getValidUntil()).toMillis()) : 0;
    return new Item(value.getId().toString(), value.getObserver(),
        value.getObserverDeviceId() == null ? null : value.getObserverDeviceId().toString(),
        value.getForegroundSequence(), value.getCreatedAt(), remaining);
  }
}
