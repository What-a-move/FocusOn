package com.capstone.backend.session.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.goal.persistence.GoalRepository;
import com.capstone.backend.session.dto.SessionDtos.ActiveResponse;
import com.capstone.backend.session.dto.SessionDtos.StartRequest;
import com.capstone.backend.session.dto.SessionDtos.StartResponse;
import com.capstone.backend.session.persistence.IdempotencyRecordEntity;
import com.capstone.backend.session.persistence.IdempotencyRecordRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import com.capstone.backend.user.persistence.UserConsentEntity;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserConsentRepository;
import com.capstone.backend.user.service.OnboardingReadinessService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 세션 시작과 사용자별 활성 세션 조회를 담당한다. */
@Service
@RequiredArgsConstructor
public class SessionService {
  private static final long MIN_DURATION_MS = 300_000L;
  private static final long MAX_DURATION_MS = 18_000_000L;
  private static final long MINUTE_MS = 60_000L;
  private static final String START_SCOPE = "CREATE_SESSION";
  private static final String POLICY_VERSION = "v1";
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
  private static final List<SessionStatus> ACTIVE_STATUSES = List.of(SessionStatus.RUNNING,
      SessionStatus.PAUSED, SessionStatus.AUTO_PAUSED, SessionStatus.AWAITING_END_CONFIRMATION,
      SessionStatus.ENDING);

  private final UserRepository users;
  private final UserDeviceRepository devices;
  private final GoalRepository goals;
  private final LearningSessionRepository sessions;
  private final IdempotencyRecordRepository records;
  private final OnboardingReadinessService onboarding;
  private final UserConsentRepository consents;
  private final ObjectMapper mapper;

  /** 같은 사용자의 시작 요청을 직렬화하고 성공 응답을 원자적으로 기록한다. */
  @Transactional
  public StartResponse start(FocusOnPrincipal principal, String deviceId, String key,
      StartRequest request) {
    DeviceHeaderValidator.require(deviceId, principal);
    if (principal.deviceType() != UserDeviceEntity.DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    UUID idempotencyKey = parseKey(key);
    validateRequest(request);
    var user = users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    String hash = requestHash(principal.deviceId() + ":" + mapper.writeValueAsString(request));
    Instant now = Instant.now();
    var previous = records.findByUserIdAndScopeAndIdempotencyKey(user.getId(), START_SCOPE,
        idempotencyKey);
    if (previous.isPresent()) {
      var record = previous.get();
      if (record.getExpiresAt().isAfter(now)) {
        if (!record.getRequestHash().equals(hash)) {
          throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return mapper.readValue(record.getResponseBodyJson(), StartResponse.class);
      }
      records.delete(record);
      records.flush();
    }
    var steps = onboarding.get(principal, null).steps();
    boolean cameraAllowed = !request.sessionOptions().cameraAnalysisEnabled()
        || consents.findByUserIdAndConsentType(user.getId(), ConsentType.CAMERA_ANALYSIS)
            .map(UserConsentEntity::isGranted).orElse(false);
    if (!steps.profileCompleted() || !steps.extensionLinked() || !steps.requiredConsentsCompleted()
        || !steps.analysisExcludedDomainsStepCompleted()
        || !request.startReadiness().screenAnalysisReady() || !cameraAllowed) {
      throw new ApiException(ApiErrorCode.SESSION_START_REQUIREMENT_MISSING);
    }
    var goal = goals.findByIdAndUserIdAndDeletedAtIsNull(parseGoalId(request.goalId()),
        user.getId()).filter(g -> g.getStatus() == GoalEntity.Status.ACTIVE)
        .orElseThrow(() -> new ApiException(ApiErrorCode.GOAL_NOT_FOUND));
    var device = devices.findById(principal.deviceId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    if (sessions.findByUserIdAndStatusIn(user.getId(), ACTIVE_STATUSES).isPresent()) {
      throw new ApiException(ApiErrorCode.SESSION_ACTIVE_EXISTS);
    }
    ZoneId zone = zone(request.timeZone());
    var session = sessions.save(LearningSessionEntity.create(user, goal, device,
        request.targetDurationMs().longValueExact(), zone.getId(), now.atZone(zone).toLocalDate(),
        mapper.writeValueAsString(request.sessionOptions()), POLICY_VERSION, now));
    var response = new StartResponse(session.getId().toString(), session.getStatus(),
        session.getVersion(), session.getTargetDurationMs(), session.getAccumulatedActiveMs(),
        session.getStartedAt(), session.getInterventionPolicyVersion());
    records.save(IdempotencyRecordEntity.create(user, START_SCOPE, idempotencyKey, hash,
        (short) 201, mapper.writeValueAsString(response), now, now.plus(Duration.ofHours(24))));
    return response;
  }

  /** 인증한 사용자에게 속한 활성 세션만 반환한다. */
  @Transactional(readOnly = true)
  public Optional<ActiveResponse> active(FocusOnPrincipal principal) {
    return sessions.findByUserIdAndStatusIn(principal.userId(), ACTIVE_STATUSES)
        .map(s -> new ActiveResponse(s.getId().toString(), s.getStatus(), s.getVersion(),
            s.getGoalTextSnapshot(), s.getTargetDurationMs(), s.getAccumulatedActiveMs()));
  }

  private void validateRequest(StartRequest request) {
    long duration = request.targetDurationMs().longValueExact();
    var channels = request.sessionOptions().notificationChannels();
    if (duration < MIN_DURATION_MS || duration > MAX_DURATION_MS || duration % MINUTE_MS != 0
        || Set.copyOf(channels).size() != channels.size()) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
  }

  private UUID parseKey(String value) {
    try {
      UUID key = UUID.fromString(value);
      if (!key.toString().equalsIgnoreCase(value)) {
        throw new IllegalArgumentException();
      }
      return key;
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new ApiException(ApiErrorCode.SESSION_IDEMPOTENCY_REQUIRED);
    }
  }

  private long parseGoalId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) {
        throw new NumberFormatException();
      }
      return id;
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.GOAL_NOT_FOUND);
    }
  }

  private ZoneId zone(String value) {
    return value != null && ZoneId.getAvailableZoneIds().contains(value)
        ? ZoneId.of(value) : DEFAULT_ZONE;
  }

  private String requestHash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
