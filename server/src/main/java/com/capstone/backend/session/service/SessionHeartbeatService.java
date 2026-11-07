package com.capstone.backend.session.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.session.dto.SessionDtos.HeartbeatRequest;
import com.capstone.backend.session.dto.SessionDtos.HeartbeatResponse;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 실행 기기의 생존 신호와 서버 확정 시간을 관리한다. */
@Service
@RequiredArgsConstructor
public class SessionHeartbeatService {
  private static final Duration ABANDONED_AFTER = Duration.ofSeconds(180);
  private static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(30);
  private final UserRepository users;
  private final LearningSessionRepository sessions;
  private final TransactionTemplate transactions;

  /** 만료 확정은 오류 응답을 보내기 전에 별도 트랜잭션에서 커밋한다. */
  public HeartbeatResponse heartbeat(FocusOnPrincipal principal, String deviceId,
      String rawSessionId, HeartbeatRequest request) {
    DeviceHeaderValidator.require(deviceId, principal);
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    long sessionId = parseSessionId(rawSessionId);
    HeartbeatOutcome outcome = transactions.execute(status ->
        process(principal, sessionId, request));
    if (outcome.abandoned()) {
      throw ApiException.withDetails(ApiErrorCode.SESSION_ABANDONED,
          Map.of("currentStatus", SessionStatus.ABANDONED, "endReason", "HEARTBEAT_TIMEOUT"));
    }
    return outcome.response();
  }

  private HeartbeatOutcome process(FocusOnPrincipal principal, long sessionId,
      HeartbeatRequest request) {
    users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    var session = sessions.findOwnedForUpdate(sessionId, principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
    if (!session.getRunnerDevice().getId().equals(principal.deviceId())) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
    if (session.getStatus() == SessionStatus.ABANDONED) {
      return new HeartbeatOutcome(null, true);
    }
    if (session.getStatus() != SessionStatus.RUNNING) {
      if (session.getStatus() == SessionStatus.PAUSED
          || session.getStatus() == SessionStatus.AUTO_PAUSED
          || session.getStatus() == SessionStatus.AWAITING_END_CONFIRMATION) {
        return new HeartbeatOutcome(response(session), false);
      }
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    Instant now = Instant.now();
    Instant baseline = session.getLastResumedAt();
    if (baseline == null) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    Instant lastReceived = session.getLastHeartbeatAt();
    if (lastReceived != null && lastReceived.isAfter(baseline)) {
      baseline = lastReceived;
    }
    if (!baseline.plus(ABANDONED_AFTER).isAfter(now)) {
      session.abandon(now);
      sessions.flush();
      return new HeartbeatOutcome(null, true);
    }
    // PostgreSQL은 마이크로초까지만 저장하므로 같은 입력의 재전송을 동일하게 비교한다.
    Instant observed = request.clientObservedAt().truncatedTo(ChronoUnit.MICROS);
    if (observed.isAfter(now.plus(CLOCK_TOLERANCE))) {
      throw new ApiException(ApiErrorCode.SESSION_HEARTBEAT_INVALID);
    }
    Instant previous = session.getLastHeartbeatObservedAt();
    if (previous != null && !observed.isAfter(previous)) {
      return new HeartbeatOutcome(response(session), false);
    }
    long active = request.accumulatedActiveMs();
    long elapsed = Math.max(0, Duration.between(session.getLastResumedAt(), now).toMillis());
    long delta = active - session.getAccumulatedAtResumeMs();
    if (active < session.getAccumulatedActiveMs() || delta > elapsed + CLOCK_TOLERANCE.toMillis()) {
      throw new ApiException(ApiErrorCode.SESSION_HEARTBEAT_INVALID);
    }
    boolean reached = active >= session.getTargetDurationMs();
    sessions.recordHeartbeat(sessionId, now, observed, active, reached);
    return new HeartbeatOutcome(new HeartbeatResponse(reached
        ? SessionStatus.AWAITING_END_CONFIRMATION : SessionStatus.RUNNING, now, active, null),
        false);
  }

  private HeartbeatResponse response(LearningSessionEntity session) {
    return new HeartbeatResponse(session.getStatus(), session.getLastHeartbeatAt(),
        session.getAccumulatedActiveMs(), session.getPauseReason());
  }

  private long parseSessionId(String value) {
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

  private record HeartbeatOutcome(HeartbeatResponse response, boolean abandoned) {}
}
