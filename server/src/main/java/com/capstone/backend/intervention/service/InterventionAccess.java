package com.capstone.backend.intervention.service;

import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.security.TokenFamilyGuard;
import com.capstone.backend.auth.service.AuthRateLimiter;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 전경·receipt 요청은 사용자→세션 잠금 아래 최신 인증을 검증한다. */
@Component
@RequiredArgsConstructor
public class InterventionAccess {
  private final EntityManager entityManager;
  private final UserRepository users;
  private final TokenFamilyRepository families;
  private final TokenFamilyGuard guard;
  private final LearningSessionRepository sessions;
  private final AuthRateLimiter limits;

  /** 일반 인증 필터 이후에도 unlink/동의 변경과 같은 계정 잠금 순서를 지킨다. */
  public UserDeviceEntity authenticate(FocusOnPrincipal principal, boolean extensionAllowed) {
    entityManager.clear();
    if (!extensionAllowed && principal.deviceType() != UserDeviceEntity.DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    Instant now = Instant.now();
    var family = families.findById(principal.tokenFamilyId()).filter(value ->
        value.getUser().getId().equals(principal.userId())
            && value.getDevice().getId().equals(principal.deviceId())
            && value.getDevice().getDeviceType() == principal.deviceType()
            && guard.isActive(value, now)
            && guard.hasActiveLinkedDesktop(value.getDevice(), principal.userId(), now))
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    limits.check("intervention-control:" + principal.userId() + ":" + principal.deviceId(),
        60, Duration.ofMinutes(1));
    return family.getDevice();
  }

  /** 연결 Extension도 같은 세션 runner Desktop에 소속돼 있어야 한다. */
  public LearningSessionEntity owned(FocusOnPrincipal principal, UserDeviceEntity device,
      long sessionId) {
    Long runnerId = device.getDeviceType() == UserDeviceEntity.DeviceType.DESKTOP
        ? device.getId() : device.getLinkedDesktopDevice().getId();
    return sessions.findOwnedForUpdate(sessionId, principal.userId())
        .filter(value -> value.getRunnerDevice().getId().equals(runnerId))
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
  }

  /** 공개 식별자는 UUID가 아닌 양의 BIGINT 문자열이다. */
  public static long id(String raw, ApiErrorCode code) {
    try {
      long value = Long.parseLong(raw);
      if (value > 0) {
        return value;
      }
    } catch (NumberFormatException ignored) {
      // 값이나 예외 전문을 로그에 기록하지 않는다.
    }
    throw new ApiException(code);
  }
}
