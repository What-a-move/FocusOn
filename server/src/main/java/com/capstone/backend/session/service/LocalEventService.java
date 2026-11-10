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
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.LocalEventEntity;
import com.capstone.backend.session.persistence.LocalEventRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 사용자 잠금 뒤 권한 유실 이력과 자동 정지를 한 트랜잭션으로 확정한다. */
@Service
@RequiredArgsConstructor
public class LocalEventService {
  private static final Instant MIN_OCCURRED_AT = Instant.parse("0001-01-01T00:00:00Z");
  private static final Instant MAX_OCCURRED_AT = Instant.parse("9999-12-31T23:59:59Z");
  private static final int MAX_EVENTS_PER_SESSION = 256;
  private static final Duration ABANDONED_AFTER = Duration.ofSeconds(180);
  private final TransactionTemplate transactions;
  private final UserRepository users;
  private final TokenFamilyRepository families;
  private final TokenFamilyGuard familyGuard;
  private final LearningSessionRepository sessions;
  private final LocalEventRepository events;

  /** 인증과 연결을 잠금 안에서 다시 확인한 뒤 최초 이벤트만 상태에 반영한다. */
  public Response receive(FocusOnPrincipal principal, String deviceId, String sessionId,
      Request request) {
    DeviceHeaderValidator.require(deviceId, principal);
    boolean desktop = principal.deviceType() == DeviceType.DESKTOP;
    if (desktop != (request.localEventCode() == Code.LOCAL_SCREEN_PERMISSION_REVOKED)
        || request.occurredAt().isBefore(MIN_OCCURRED_AT)
        || request.occurredAt().isAfter(MAX_OCCURRED_AT)) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    Result result = transactions.execute(status -> process(principal, sessionId, request));
    if (result.error() != null) {
      Map<String, Object> details = result.error() == ApiErrorCode.SESSION_ABANDONED
          ? Map.of("currentStatus", result.response().status(), "endReason", "HEARTBEAT_TIMEOUT")
          : Map.of("currentStatus", result.response().status(),
              "currentVersion", result.response().version());
      throw ApiException.withDetails(result.error(), details);
    }
    return result.response();
  }

  /** 상태 변경은 오류 응답보다 먼저 커밋해 만료나 필수 정지가 롤백되지 않게 한다. */
  private Result process(FocusOnPrincipal principal, String sessionId, Request request) {
    boolean desktop = principal.deviceType() == DeviceType.DESKTOP;
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
    if (session.getStatus() == SessionStatus.RUNNING && expired(session, now)) {
      session.abandon(now);
      sessions.flush();
      return new Result(response(session), ApiErrorCode.SESSION_ABANDONED);
    }
    String hash = hash(request);
    if (!events.existsBySessionIdAndDeviceIdAndEventHash(session.getId(), device.getId(), hash)) {
      if (session.getStatus() == SessionStatus.ENDED
          || session.getStatus() == SessionStatus.ABANDONED
          || session.getStatus() == SessionStatus.ENDING) {
        throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
      }
      boolean full = events.countBySessionId(session.getId()) >= MAX_EVENTS_PER_SESSION;
      if (!full) {
        events.save(LocalEventEntity.create(session.getId(), device.getId(),
            request.localEventCode(), hash, request.occurredAt(), now));
      }
      if (session.getStatus() == SessionStatus.RUNNING) {
        // 본문에 최종 monotonic 시간이 없으므로 미확인 경과 시간을 더하지 않는다.
        // 확장 권한 유실은 연결이 살아 있고 사이트 접근만 잃은 것이므로 Desktop의 복구 안내가 다르다.
        session.pause(SessionStatus.AUTO_PAUSED, PauseReason.REQUIRED_PERMISSION.name(),
            session.getAccumulatedActiveMs());
        sessions.flush();
      }
      if (full) {
        return new Result(response(session), ApiErrorCode.SESSION_LOCAL_EVENT_LIMIT_EXCEEDED);
      }
    }
    return new Result(response(session), null);
  }

  /** 현재 실행 구간과 서버가 마지막으로 받은 신호 중 최신 시각을 기준으로 한다. */
  private boolean expired(LearningSessionEntity session, Instant now) {
    Instant baseline = session.getLastResumedAt();
    if (baseline == null) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    if (session.getLastHeartbeatAt() != null && session.getLastHeartbeatAt().isAfter(baseline)) {
      baseline = session.getLastHeartbeatAt();
    }
    return !baseline.plus(ABANDONED_AFTER).isAfter(now);
  }

  /** 트랜잭션 밖으로 Entity를 전달하지 않는다. */
  private Response response(LearningSessionEntity session) {
    return new Response(session.getStatus(), session.getVersion(),
        session.getAccumulatedActiveMs(), session.getPauseReason());
  }

  private record Result(Response response, ApiErrorCode error) {}

  /** 잘못된 식별자도 세션의 존재 여부를 노출하지 않는404로 처리한다. */
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

  /** 클라이언트 원래 시각의 나노초 정밀도를 보존해 재전송 키를 계산한다. */
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
