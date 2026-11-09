package com.capstone.backend.session.service;

import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.security.TokenFamilyGuard;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.session.dto.LocalEventDtos.Code;
import com.capstone.backend.session.dto.LocalEventDtos.Request;
import com.capstone.backend.session.dto.LocalEventDtos.Response;
import com.capstone.backend.session.dto.SessionDtos.PauseReason;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.LocalEventEntity;
import com.capstone.backend.session.persistence.LocalEventRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 잠금 뒤 권한 유실 이력과 자동 정지를 한 트랜잭션으로 확정한다. */
@Service
@RequiredArgsConstructor
public class LocalEventService {
  private static final Instant MIN_OCCURRED_AT = Instant.parse("0001-01-01T00:00:00Z");
  private static final Instant MAX_OCCURRED_AT = Instant.parse("9999-12-31T23:59:59Z");
  private final UserRepository users;
  private final TokenFamilyRepository families;
  private final TokenFamilyGuard familyGuard;
  private final LearningSessionRepository sessions;
  private final LocalEventRepository events;

  /** 인증과 연결을 잠금 안에서 다시 확인한 뒤 최초 이벤트만 상태에 반영한다. */
  @Transactional
  public Response receive(FocusOnPrincipal principal, String deviceId, String sessionId,
      Request request) {
    DeviceHeaderValidator.require(deviceId, principal);
    boolean desktop = principal.deviceType() == DeviceType.DESKTOP;
    if (desktop != (request.localEventCode() == Code.LOCAL_SCREEN_PERMISSION_REVOKED)
        || request.occurredAt().isBefore(MIN_OCCURRED_AT)
        || request.occurredAt().isAfter(MAX_OCCURRED_AT)) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    Instant now = Instant.now();
    var family = families.findById(principal.tokenFamilyId())
        .filter(f -> f.getUser().getId().equals(principal.userId())
            && f.getDevice().getId().equals(principal.deviceId())
            && f.getDevice().getDeviceType() == principal.deviceType()
            && familyGuard.isActive(f, now)
            && familyGuard.hasActiveLinkedDesktop(f.getDevice(), principal.userId(), now))
        .orElseThrow(() -> new ApiException(desktop ? ApiErrorCode.AUTH_RELOGIN_REQUIRED
            : ApiErrorCode.LINK_TOKEN_REVOKED));
    var device = family.getDevice();
    var session = sessions.findOwnedForUpdate(parseId(sessionId), principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
    Long runnerId = desktop ? device.getId() : device.getLinkedDesktopDevice().getId();
    if (!session.getRunnerDevice().getId().equals(runnerId)) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
    String hash = hash(request);
    if (!events.existsBySessionIdAndDeviceIdAndEventHash(session.getId(), device.getId(), hash)) {
      if (session.getStatus() == SessionStatus.ENDED
          || session.getStatus() == SessionStatus.ABANDONED
          || session.getStatus() == SessionStatus.ENDING) {
        throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
      }
      events.save(LocalEventEntity.create(session.getId(), device.getId(),
          request.localEventCode(), hash, request.occurredAt(), now));
      if (session.getStatus() == SessionStatus.RUNNING) {
        // 본문에 최종 monotonic 시간이 없으므로 미확인 경과 시간을 더하지 않는다.
        PauseReason reason = desktop
            ? PauseReason.REQUIRED_PERMISSION : PauseReason.EXTENSION_UNLINKED;
        session.pause(SessionStatus.AUTO_PAUSED, reason.name(), session.getAccumulatedActiveMs());
        sessions.flush();
      }
    }
    return new Response(session.getStatus(), session.getVersion(),
        session.getAccumulatedActiveMs(), session.getPauseReason());
  }

  private long parseId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) {
        throw new NumberFormatException();
      }
      return id;
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
  }

  private String hash(Request request) {
    try {
      byte[] bytes = (request.localEventCode() + ":" + request.occurredAt())
          .getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
