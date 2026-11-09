package com.capstone.backend.intervention.service;

import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.intervention.dto.InterventionDtos.Delivery;
import com.capstone.backend.intervention.dto.InterventionDtos.DeliveryReceipt;
import com.capstone.backend.intervention.dto.InterventionDtos.DispatchStatus;
import com.capstone.backend.intervention.persistence.InterventionDeliveryEntity;
import com.capstone.backend.intervention.persistence.InterventionDeliveryRepository;
import com.capstone.backend.intervention.persistence.InterventionEventRepository;
import com.capstone.backend.session.dto.SessionDtos.NotificationChannel;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 실제 전송 결과와 최초 서버 수신 cooldown을 사용자 ack와 분리한다. */
@Service
@RequiredArgsConstructor
public class InterventionDeliveryService {
  private final InterventionAccess access;
  private final InterventionService interventions;
  private final LearningSessionRepository sessions;
  private final InterventionEventRepository events;
  private final InterventionDeliveryRepository deliveries;
  private final ObjectMapper json;

  /** 늦은 receipt도 저장하되 기존 수명/사용자 결정을 되돌리지 않는다. */
  @Transactional
  public DeliveryReceipt receive(FocusOnPrincipal principal, String deviceId, String rawId,
      Delivery request) {
    DeviceHeaderValidator.require(deviceId, principal);
    access.authenticate(principal, false);
    var event = events.findById(InterventionAccess.id(rawId, ApiErrorCode.NOT_FOUND))
        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
    var session = sessions.findOwnedForUpdate(event.getSessionId(), principal.userId())
        .filter(value -> value.getRunnerDevice().getId().equals(principal.deviceId()))
        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
    var channels = request.channels().stream().sorted().toList();
    var allowed = Arrays.asList(json.readValue(event.getChannelsJson(),
        NotificationChannel[].class));
    if (new HashSet<>(channels).size() != channels.size() || !allowed.containsAll(channels)
        || ((request.dispatchStatus() == DispatchStatus.SENT) != !channels.isEmpty())) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    UUID nonce = UUID.fromString(request.clientDeliveryId());
    var previous = deliveries.findByInterventionIdAndClientDeliveryId(event.getId(), nonce)
        .orElse(null);
    if (previous != null) {
      if (previous.getDispatchStatus() != request.dispatchStatus()
          || !Arrays.asList(json.readValue(previous.getChannelsJson(), NotificationChannel[].class))
              .equals(channels)) {
        throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
      }
      return receipt(previous);
    }
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    if (event.getDeliveryConfirmedAt() == null
        && (!now.isBefore(event.getExpiresAt())
            || !interventions.presentable(session, event, now))) {
      event.expire();
    }
    if (request.dispatchStatus() == DispatchStatus.SENT) {
      event.confirmDelivery(now);
    }
    Instant cooldown = event.getDeliveryConfirmedAt() == null ? null
        : event.getDeliveryConfirmedAt().plusSeconds(300);
    var saved = deliveries.saveAndFlush(InterventionDeliveryEntity.create(event.getId(), nonce,
        request.dispatchStatus(), json.writeValueAsString(channels), now, cooldown));
    return receipt(saved);
  }

  private DeliveryReceipt receipt(InterventionDeliveryEntity value) {
    return new DeliveryReceipt(value.getDispatchStatus(), value.getAcceptedAt(),
        value.getCooldownUntil());
  }
}
