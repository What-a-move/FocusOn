package com.capstone.backend.intervention.service;

import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.security.TokenFamilyGuard;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.intervention.dto.InterventionDtos.Acknowledge;
import com.capstone.backend.intervention.dto.InterventionDtos.Acknowledgement;
import com.capstone.backend.intervention.dto.InterventionDtos.Item;
import com.capstone.backend.intervention.dto.InterventionDtos.Status;
import com.capstone.backend.intervention.persistence.InterventionEventEntity;
import com.capstone.backend.intervention.persistence.InterventionEventRepository;
import com.capstone.backend.intervention.persistence.InterventionPolicyRepository;
import com.capstone.backend.session.dto.SessionDtos.NotificationChannel;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 조회는 발송 확인이 아니며 사용자 반응은 세션 상태를 변경하지 않는다. */
@Service
@RequiredArgsConstructor
public class InterventionService {
  private final EntityManager entityManager;
  private final UserRepository users;
  private final TokenFamilyRepository families;
  private final TokenFamilyGuard guard;
  private final LearningSessionRepository sessions;
  private final InterventionEventRepository events;
  private final InterventionPolicyRepository states;
  private final InterventionPolicyService policy;
  private final ObjectMapper json;

  /** 원본 계약의 data 배열이며 조회 부수효과로 cooldown을 시작하지 않는다. */
  @Transactional
  public List<Item> list(FocusOnPrincipal principal, String rawSessionId) {
    entityManager.clear();
    authenticate(principal);
    var session = owned(principal, id(rawSessionId, ApiErrorCode.SESSION_NOT_FOUND));
    Instant now = Instant.now();
    return events.findBySessionIdOrderByCreatedAtAscIdAsc(session.getId()).stream()
        .filter(event -> event.getStatus() != Status.PENDING
            || event.getDeliveryConfirmedAt() == null)
        .filter(event -> event.getStatus() != Status.PENDING || !now.isBefore(event.getExpiresAt())
            || presentable(session, event, now))
        .map(event -> item(event, now)).toList();
  }

  /** 같은/반대 action 재전송 모두 최초 결정·actedAt을 유지한다. */
  @Transactional
  public Acknowledgement acknowledge(FocusOnPrincipal principal, String deviceId, String rawId,
      Acknowledge request) {
    DeviceHeaderValidator.require(deviceId, principal);
    entityManager.clear();
    authenticate(principal);
    // 사용자 잠금 뒤 조회하므로 같은 계정의 ack/정책 생성도 일관된 순서로 직렬화한다.
    var event = events.findById(id(rawId, ApiErrorCode.NOT_FOUND))
        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
    var session = sessions.findOwnedForUpdate(event.getSessionId(), principal.userId())
        .filter(value -> value.getRunnerDevice().getId().equals(principal.deviceId()))
        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    if (event.getDeliveryConfirmedAt() == null
        && (!now.isBefore(event.getExpiresAt()) || !presentable(session, event, now))) {
      event.expire();
    }
    event.acknowledge(request.action(), now);
    return new Acknowledgement(event.getStatus(), event.getActedAt());
  }

  boolean presentable(LearningSessionEntity session, InterventionEventEntity event,
      Instant now) {
    if (!Objects.equals(session.getLastResumedAt(), event.getRunningSince())) {
      return false;
    }
    var context = policy.binding(session, now).orElse(null);
    var state = states.findById(session.getId()).orElse(null);
    if (context == null || !context.deliveryContractReady() || state == null) {
      return false;
    }
    var timeline = json.readValue(state.getTimelineJson(), OffTaskTimeline.Snapshot.class);
    return event.getObservationContextId() != null
        && event.getObservationContextId().toString().equals(context.context())
        && timeline.pendingRun() == null && timeline.openEpisode() != null
        && timeline.context().equals(context.context());
  }

  private Item item(InterventionEventEntity event, Instant now) {
    Status status = event.getStatus() == Status.PENDING && !now.isBefore(event.getExpiresAt())
        ? Status.EXPIRED : event.getStatus();
    return new Item(event.getId().toString(), event.getType(), status,
        Arrays.asList(json.readValue(event.getChannelsJson(), NotificationChannel[].class)),
        event.getCreatedAt(), event.getPolicyVersion(),
        event.getObservationContextId() == null ? null : event.getObservationContextId().toString(),
        status == Status.PENDING && event.getDeliveryConfirmedAt() == null
            ? Math.max(0, Duration.between(now, event.getExpiresAt()).toMillis()) : 0);
  }

  private void authenticate(FocusOnPrincipal principal) {
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    families.findById(principal.tokenFamilyId()).filter(family ->
        family.getUser().getId().equals(principal.userId())
            && family.getDevice().getId().equals(principal.deviceId())
            && family.getDevice().getDeviceType() == DeviceType.DESKTOP
            && guard.isActive(family, Instant.now()))
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
  }

  private LearningSessionEntity owned(FocusOnPrincipal principal, long id) {
    return sessions.findOwnedForUpdate(id, principal.userId())
        .filter(session -> session.getRunnerDevice().getId().equals(principal.deviceId()))
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
  }

  private long id(String value, ApiErrorCode error) {
    try {
      long id = Long.parseLong(value);
      if (id > 0) {
        return id;
      }
    } catch (NumberFormatException ignored) {
      // 공개 BIGINT 식별자를 UUID로 검증하지 않는다.
    }
    throw new ApiException(error);
  }
}
