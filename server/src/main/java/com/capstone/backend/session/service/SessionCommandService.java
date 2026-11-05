package com.capstone.backend.session.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.session.dto.SessionDtos.EndResponse;
import com.capstone.backend.session.dto.SessionDtos.PauseReason;
import com.capstone.backend.session.dto.SessionDtos.PauseRequest;
import com.capstone.backend.session.dto.SessionDtos.PauseResponse;
import com.capstone.backend.session.dto.SessionDtos.ResumeRequest;
import com.capstone.backend.session.dto.SessionDtos.ResumeResponse;
import com.capstone.backend.session.persistence.IdempotencyRecordEntity;
import com.capstone.backend.session.persistence.IdempotencyRecordRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import com.capstone.backend.user.service.OnboardingReadinessService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 일시정지·재개 명령의 상태 전이와 멱등성을 처리한다. */
@Service
@RequiredArgsConstructor
public class SessionCommandService {
  private static final String RESUME_SCOPE = "RESUME_SESSION";
  private static final String PAUSE_SCOPE = "PAUSE_SESSION";
  private static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(24);
  private static final long CLOCK_TOLERANCE_MS = 30_000L;

  private final UserRepository users;
  private final LearningSessionRepository sessions;
  private final IdempotencyRecordRepository records;
  private final OnboardingReadinessService onboarding;
  private final ObjectMapper mapper;

  /** 실행 구간의 검증된 누적 시간을 저장하고 일시정지한다. */
  @Transactional
  public PauseResponse pause(FocusOnPrincipal principal, String sessionId, String key,
      String versionHeader, PauseRequest request) {
    requireDesktop(principal);
    UUID idempotencyKey = SessionService.parseKey(key);
    long expectedVersion = parseVersion(versionHeader);
    var user = users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    var session = findOwnedSession(principal, sessionId);
    String hash = SessionService.requestHash(principal.deviceId() + ":" + sessionId + ":"
        + expectedVersion + ":" + mapper.writeValueAsString(request));
    Instant now = Instant.now();
    var previous = records.findByUserIdAndScopeAndIdempotencyKey(user.getId(), PAUSE_SCOPE,
        idempotencyKey);
    if (previous.isPresent()) {
      var record = previous.get();
      if (record.getExpiresAt().isAfter(now)) {
        if (!record.getRequestHash().equals(hash)) {
          throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return mapper.readValue(record.getResponseBodyJson(), PauseResponse.class);
      }
      records.delete(record);
      records.flush();
    }
    requireVersion(session, expectedVersion);
    if (session.getStatus() != SessionStatus.RUNNING) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    long activeMs = validatedPauseTime(session, request, now);
    SessionStatus pausedStatus = request.pauseReason() == PauseReason.USER
        ? SessionStatus.PAUSED : SessionStatus.AUTO_PAUSED;
    session.pause(pausedStatus, request.pauseReason().name(), activeMs);
    sessions.flush();
    var response = new PauseResponse(session.getStatus(), session.getVersion(), activeMs);
    records.save(IdempotencyRecordEntity.create(user, PAUSE_SCOPE, idempotencyKey, hash,
        (short) 200, mapper.writeValueAsString(response), now, now.plus(IDEMPOTENCY_RETENTION)));
    return response;
  }

  /** 마지막 검증 누적값으로 사용자 종료를 확정하고 동일 명령의 응답을 보존한다. */
  @Transactional
  public EndResponse end(FocusOnPrincipal principal, String sessionId, String key,
      String versionHeader) {
    requireDesktop(principal);
    UUID idempotencyKey = SessionService.parseKey(key);
    long expectedVersion = parseVersion(versionHeader);
    var user = users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    var session = findOwnedSession(principal, sessionId);
    String scope = "END_SESSION";
    String hash = SessionService.requestHash(principal.deviceId() + ":" + sessionId + ":"
        + expectedVersion);
    Instant now = Instant.now();
    var previous = records.findByUserIdAndScopeAndIdempotencyKey(user.getId(), scope,
        idempotencyKey);
    if (previous.isPresent()) {
      var record = previous.get();
      if (record.getExpiresAt().isAfter(now)) {
        if (!record.getRequestHash().equals(hash)) {
          throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return mapper.readValue(record.getResponseBodyJson(), EndResponse.class);
      }
      records.delete(record);
      records.flush();
    }
    requireVersion(session, expectedVersion);
    if (session.getStatus() != SessionStatus.RUNNING
        && session.getStatus() != SessionStatus.PAUSED
        && session.getStatus() != SessionStatus.AUTO_PAUSED
        && session.getStatus() != SessionStatus.AWAITING_END_CONFIRMATION) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    session.end(now);
    sessions.flush();
    var response = new EndResponse(session.getStatus(), session.getVersion(),
        session.getAccumulatedActiveMs(), session.getTargetDurationMs(), session.getEndedAt());
    records.save(IdempotencyRecordEntity.create(user, scope, idempotencyKey, hash,
        (short) 200, mapper.writeValueAsString(response), now, now.plus(IDEMPOTENCY_RETENTION)));
    return response;
  }

  private long validatedPauseTime(LearningSessionEntity session, PauseRequest request,
      Instant now) {
    long confirmed = session.getAccumulatedActiveMs();
    if (request.pauseReason() == PauseReason.APP_RESTART) {
      return confirmed;
    }
    if (session.getLastResumedAt() == null || request.accumulatedActiveMs() < confirmed) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    long elapsed = Math.max(0, Duration.between(session.getLastResumedAt(), now).toMillis());
    long delta = request.accumulatedActiveMs() - session.getAccumulatedAtResumeMs();
    if (delta > elapsed && delta - elapsed > CLOCK_TOLERANCE_MS) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    return request.accumulatedActiveMs();
  }

  /** 인증된 실행 기기에서 명시적으로 요청할 때만 세션을 재개한다. */
  @Transactional
  public ResumeResponse resume(FocusOnPrincipal principal, String sessionId, String key,
      String versionHeader, ResumeRequest request) {
    requireDesktop(principal);
    UUID idempotencyKey = SessionService.parseKey(key);
    long expectedVersion = parseVersion(versionHeader);
    var user = users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    var session = findOwnedSession(principal, sessionId);
    String hash = SessionService.requestHash(principal.deviceId() + ":" + sessionId + ":"
        + expectedVersion + ":" + mapper.writeValueAsString(request));
    Instant now = Instant.now();
    var previous = records.findByUserIdAndScopeAndIdempotencyKey(user.getId(), RESUME_SCOPE,
        idempotencyKey);
    if (previous.isPresent()) {
      var record = previous.get();
      if (record.getExpiresAt().isAfter(now)) {
        if (!record.getRequestHash().equals(hash)) {
          throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return mapper.readValue(record.getResponseBodyJson(), ResumeResponse.class);
      }
      records.delete(record);
      records.flush();
    }
    requireVersion(session, expectedVersion);
    if (session.getStatus() != SessionStatus.PAUSED
        && session.getStatus() != SessionStatus.AUTO_PAUSED) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    var steps = onboarding.get(principal, null).steps();
    if (!steps.extensionLinked() || !steps.requiredConsentsCompleted()
        || !request.readiness().screenAnalysisReady()) {
      throw ApiException.resumeRequirementMissing();
    }
    session.resume(now);
    sessions.flush();
    var response = new ResumeResponse(session.getStatus(), session.getVersion(), now);
    records.save(IdempotencyRecordEntity.create(user, RESUME_SCOPE, idempotencyKey, hash,
        (short) 200, mapper.writeValueAsString(response), now, now.plus(IDEMPOTENCY_RETENTION)));
    return response;
  }

  private void requireDesktop(FocusOnPrincipal principal) {
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
  }

  private LearningSessionEntity findOwnedSession(FocusOnPrincipal principal, String id) {
    long sessionId;
    try {
      sessionId = Long.parseLong(id);
      if (sessionId <= 0) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
    var session = sessions.findByIdAndUserId(sessionId, principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
    if (!session.getRunnerDevice().getId().equals(principal.deviceId())) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
    return session;
  }

  private long parseVersion(String value) {
    try {
      long version = Long.parseLong(value);
      if (version < 0) {
        throw new NumberFormatException();
      }
      return version;
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
  }

  private void requireVersion(LearningSessionEntity session, long version) {
    if (session.getVersion() != version) {
      throw ApiException.withDetails(ApiErrorCode.SESSION_VERSION_CONFLICT,
          Map.of("currentStatus", session.getStatus(), "currentVersion", session.getVersion()));
    }
  }
}
