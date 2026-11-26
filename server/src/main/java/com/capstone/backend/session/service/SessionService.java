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
import com.capstone.backend.session.dto.SessionDtos.AddGoalRequest;
import com.capstone.backend.session.dto.SessionDtos.SessionGoal;
import com.capstone.backend.session.dto.SessionDtos.SessionGoalsResponse;
import com.capstone.backend.session.dto.SessionDtos.StartRequest;
import com.capstone.backend.session.dto.SessionDtos.StartResponse;
import com.capstone.backend.session.persistence.IdempotencyRecordEntity;
import com.capstone.backend.session.persistence.IdempotencyRecordRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionGoalEntity;
import com.capstone.backend.session.persistence.SessionGoalRepository;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
  /** 새 세션은 판단 보류 구간을 멈춤으로 처리하는 v2 정책을 쓴다(#296). 진행 중 세션은 v1 유지. */
  private static final String POLICY_VERSION =
      com.capstone.backend.intervention.service.OffTaskTimeline.POLICY_V2;
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

  private final UserRepository users;
  private final UserDeviceRepository devices;
  private final GoalRepository goals;
  private final LearningSessionRepository sessions;
  private final SessionGoalRepository sessionGoals;
  private final IdempotencyRecordRepository records;
  private final OnboardingReadinessService onboarding;
  private final UserConsentRepository consents;
  private final ObjectMapper mapper;

  /** 같은 사용자의 시작 요청을 직렬화하고 성공 응답을 원자적으로 기록한다. */
  @Transactional
  public StartResponse start(FocusOnPrincipal principal, String deviceId, String key,
      StartRequest request) {
    // 요청 헤더가 인증된 기기와 다르면 다른 기기를 대신 실행할 수 없게 막는다.
    DeviceHeaderValidator.require(deviceId, principal);
    // 세션 시작은 Desktop만 가능하며 Extension 토큰은 허용하지 않는다.
    if (principal.deviceType() != UserDeviceEntity.DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    UUID idempotencyKey = parseKey(key);
    // 사용자 행 잠금으로 같은 사용자의 동시 시작과 멱등 키 처리를 직렬화한다.
    var user = users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    // 기기 ID도 해시에 포함해 다른 기기의 요청을 같은 재시도로 취급하지 않는다.
    String hash = requestHash(principal.deviceId() + ":" + mapper.writeValueAsString(request));
    Instant now = Instant.now();
    var previous = records.findByUserIdAndScopeAndIdempotencyKey(user.getId(), START_SCOPE,
        idempotencyKey);
    if (previous.isPresent()) {
      var record = previous.get();
      if (record.getExpiresAt().isAfter(now)) {
        // 같은 키를 다른 내용에 재사용하면 이전 결과를 돌려주지 않고 충돌시킨다.
        if (!record.getRequestHash().equals(hash)) {
          throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return replayStart(record.getResponseBodyJson());
      }
      // 만료된 기록은 같은 사용자·명령·키의 유일 제약에 걸리지 않도록 먼저 삭제한다.
      records.delete(record);
      records.flush();
    }
    validateRequest(request);
    // 동일 요청 재시도는 위에서 처리했고, 새 요청은 활성 세션이 없어야 한다.
    if (sessions.findByUserIdAndStatusIn(user.getId(), SessionStatus.activeStatuses())
        .isPresent()) {
      throw new ApiException(ApiErrorCode.SESSION_ACTIVE_EXISTS);
    }
    var steps = onboarding.get(principal, null).steps();
    boolean cameraAllowed = !request.sessionOptions().cameraAnalysisEnabled()
        || consents.findByUserIdAndConsentType(user.getId(), ConsentType.CAMERA_ANALYSIS)
            .map(UserConsentEntity::isGranted).orElse(false);
    // 화면 준비는 Desktop의 현재 확인값이며 연결과 동의는 서버 저장 상태로 판단한다.
    if (!steps.profileCompleted() || !steps.extensionLinked() || !steps.requiredConsentsCompleted()
        || !steps.analysisExcludedDomainsStepCompleted()
        || !request.startReadiness().screenAnalysisReady() || !cameraAllowed) {
      throw new ApiException(ApiErrorCode.SESSION_START_REQUIREMENT_MISSING);
    }
    // 존재하지 않는 목표와 다른 사용자의 목표를 모두 같은 404로 처리한다.
    var selectedGoals = requestedGoalIds(request).stream()
        .map(id -> activeGoal(user.getId(), id)).toList();
    var goal = selectedGoals.getFirst();
    var device = devices.findById(principal.deviceId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    ZoneId zone = zone(request.timeZone());
    Instant startedAt = Instant.now();
    // 목표 문구·시간대·옵션을 시작 시점에 고정해 이후 설정 변경과 분리한다.
    var session = sessions.save(LearningSessionEntity.create(user, goal, device,
        request.targetDurationMs().longValueExact(), zone.getId(),
        startedAt.atZone(zone).toLocalDate(), mapper.writeValueAsString(request.sessionOptions()),
        POLICY_VERSION, startedAt));
    List<SessionGoalEntity> snapshots = new ArrayList<>(selectedGoals.size());
    for (int index = 0; index < selectedGoals.size(); index++) {
      snapshots.add(SessionGoalEntity.snapshot(session.getId(), index + 1,
          selectedGoals.get(index)));
    }
    sessionGoals.saveAll(snapshots);
    var response = new StartResponse(session.getId().toString(), session.getStatus(),
        session.getVersion(), session.getTargetDurationMs(), session.getAccumulatedActiveMs(),
        session.getStartedAt(), session.getInterventionPolicyVersion(), publicGoals(snapshots),
        session.getGoalVersion());
    // 세션과 응답을 같은 트랜잭션에 저장해야 중복 요청이 최초 응답을 재현할 수 있다.
    records.save(IdempotencyRecordEntity.create(user, START_SCOPE, idempotencyKey, hash,
        (short) 201, mapper.writeValueAsString(response), startedAt,
        startedAt.plus(Duration.ofHours(24))));
    return response;
  }

  private StartResponse replayStart(String responseJson) {
    var response = mapper.readValue(responseJson, StartResponse.class);
    if (response.goalVersion() > 0) {
      return response;
    }
    var session = sessions.findById(Long.valueOf(response.sessionId()))
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
    return new StartResponse(response.sessionId(), response.status(), response.version(),
        response.targetDurationMs(), response.accumulatedActiveMs(), response.startedAt(),
        response.interventionPolicyVersion(),
        publicGoals(List.of(SessionGoalEntity.legacySnapshot(session))), 1);
  }

  /**
   * 실행 Desktop만 목표를 추가하거나 교체한다. 이미 반영된 요청은 안전하게 재시도할 수 있다.
   * 교체는 replaceGoalId의 자리를 새 할 일 사본으로 덮어쓰고 goalVersion을 올린다(#320).
   */
  @Transactional
  public SessionGoalsResponse addGoal(FocusOnPrincipal principal, String deviceId,
      String sessionId, AddGoalRequest request) {
    DeviceHeaderValidator.require(deviceId, principal);
    if (principal.deviceType() != UserDeviceEntity.DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    long id;
    try {
      id = Long.parseLong(sessionId);
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
    users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    var session = sessions.findOwnedForUpdate(id, principal.userId())
        .filter(row -> row.getRunnerDevice().getId().equals(principal.deviceId()))
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
    if (session.getStatus() != SessionStatus.RUNNING) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    long goalId = parseGoalId(request.goalId());
    long version = session.getGoalVersion();
    var snapshots = sessionGoals.findByIdSessionIdOrderByIdPositionAsc(id);
    var ordered = new ArrayList<>(snapshots.isEmpty()
        ? List.of(SessionGoalEntity.legacySnapshot(session)) : snapshots);
    if (request.replaceGoalId() != null) {
      return replaceGoal(principal.userId(), id, request, goalId, version, snapshots, ordered);
    }
    if (request.goalVersion() <= version
        && ordered.stream().anyMatch(row -> row.getGoalId() == goalId)) {
      return new SessionGoalsResponse(publicGoals(ordered), version);
    }
    if (request.goalVersion() != version) {
      throw new ApiException(ApiErrorCode.SESSION_VERSION_CONFLICT);
    }
    if (ordered.size() == 3) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    var goal = activeGoal(principal.userId(), goalId);
    if (snapshots.isEmpty()) {
      sessionGoals.save(ordered.getFirst());
    }
    var added = SessionGoalEntity.snapshot(id, ordered.size() + 1, goal);
    sessionGoals.save(added);
    ordered.add(added);
    if (sessions.incrementGoalVersion(id, version) != 1) {
      throw new ApiException(ApiErrorCode.SESSION_VERSION_CONFLICT);
    }
    return new SessionGoalsResponse(publicGoals(ordered), version + 1);
  }

  private SessionGoalsResponse replaceGoal(long userId, long sessionId, AddGoalRequest request,
      long goalId, long version, List<SessionGoalEntity> snapshots,
      List<SessionGoalEntity> ordered) {
    long replaceId = parseGoalId(request.replaceGoalId());
    if (replaceId == goalId) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    boolean hasNew = ordered.stream().anyMatch(row -> row.getGoalId() == goalId);
    boolean hasOld = ordered.stream().anyMatch(row -> row.getGoalId() == replaceId);
    // 응답을 잃고 같은 교체를 다시 보내면 이미 반영된 목록을 그대로 돌려준다. 교체에 성공하면 버전이
    // 오르므로 재시도는 더 낮은 버전을 들고 온다. 같은 버전이면 교체한 적이 없는 요청이다.
    if (request.goalVersion() < version && hasNew && !hasOld) {
      return new SessionGoalsResponse(publicGoals(ordered), version);
    }
    if (request.goalVersion() != version) {
      throw new ApiException(ApiErrorCode.SESSION_VERSION_CONFLICT);
    }
    if (!hasOld || hasNew) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    var goal = activeGoal(userId, goalId);
    var target = ordered.stream().filter(row -> row.getGoalId() == replaceId).findFirst()
        .orElseThrow();
    if (snapshots.isEmpty()) {
      // 사본이 없던 옛 세션은 교체 결과를 첫 사본으로 저장한다.
      var replaced = SessionGoalEntity.snapshot(sessionId, 1, goal);
      sessionGoals.save(replaced);
      ordered.set(0, replaced);
    } else {
      target.replaceWith(goal);
      sessionGoals.save(target);
    }
    if (sessions.incrementGoalVersion(sessionId, version) != 1) {
      throw new ApiException(ApiErrorCode.SESSION_VERSION_CONFLICT);
    }
    return new SessionGoalsResponse(publicGoals(ordered), version + 1);
  }

  private List<Long> requestedGoalIds(StartRequest request) {
    if (request.goalIds() == null && request.goalId() != null) {
      return List.of(parseGoalId(request.goalId()));
    }
    if (request.goalId() != null || request.goalIds() == null || request.goalIds().isEmpty()
        || request.goalIds().size() > 3) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    var ids = request.goalIds().stream().map(this::parseGoalId).toList();
    if (Set.copyOf(ids).size() != ids.size()) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    return ids;
  }

  private GoalEntity activeGoal(long userId, long goalId) {
    return goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
        .filter(goal -> goal.getStatus() == GoalEntity.Status.ACTIVE)
        .orElseThrow(() -> new ApiException(ApiErrorCode.GOAL_NOT_FOUND));
  }

  private List<SessionGoalEntity> goalSnapshots(LearningSessionEntity session) {
    var snapshots = sessionGoals.findByIdSessionIdOrderByIdPositionAsc(session.getId());
    return snapshots.isEmpty() ? List.of(SessionGoalEntity.legacySnapshot(session)) : snapshots;
  }

  private List<SessionGoal> publicGoals(List<SessionGoalEntity> snapshots) {
    return snapshots.stream()
        .map(row -> new SessionGoal(row.getGoalId().toString(), row.getGoalTextSnapshot()))
        .toList();
  }

  /** 인증한 사용자에게 속한 활성 세션만 반환한다. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Optional<ActiveResponse> active(FocusOnPrincipal principal) {
    Instant now = Instant.now();
    return sessions.findByUserIdAndStatusIn(principal.userId(), SessionStatus.activeStatuses())
        .map(s -> new ActiveResponse(s.getId().toString(), s.getStatus(), s.getVersion(),
            s.getGoalTextSnapshot(), s.getTargetDurationMs(), s.getAccumulatedActiveMs(),
            accumulatedAt(s), now, publicGoals(goalSnapshots(s)), s.getGoalVersion()));
  }

  /**
   * 누적 시간이 확정된 서버 시각이다. 서버가 heartbeat를 받은 시각과 마지막 재개 시각 중 늦은
   * 값이며, 기기 시계인 clientObservedAt은 쓰지 않는다. RUNNING이 아니면 시간이 흐르지 않는다.
   */
  private static Instant accumulatedAt(LearningSessionEntity session) {
    if (session.getStatus() != SessionStatus.RUNNING) {
      return null;
    }
    Instant resumed = session.getLastResumedAt();
    Instant heartbeat = session.getLastHeartbeatAt();
    return heartbeat != null && (resumed == null || heartbeat.isAfter(resumed))
        ? heartbeat : resumed;
  }

  /** 목표 시간을 5~300분의 1분 단위로 제한하고 알림 채널 중복을 거부한다. */
  private void validateRequest(StartRequest request) {
    long duration = request.targetDurationMs().longValueExact();
    var channels = request.sessionOptions().notificationChannels();
    if (duration < MIN_DURATION_MS || duration > MAX_DURATION_MS || duration % MINUTE_MS != 0
        || Set.copyOf(channels).size() != channels.size()) {
      // 시간 범위·단위 또는 중복 채널이 잘못되면 공통 입력 오류를 반환한다.
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
  }

  /** 멱등 키가 표준 UUID 문자열인지 확인하고 UUID 값으로 변환한다. */
  static UUID parseKey(String value) {
    try {
      UUID key = UUID.fromString(value);
      // UUID 파서는 일부 축약 형식도 허용하므로 원래 문자열의 표준 형식을 재확인한다.
      if (!key.toString().equalsIgnoreCase(value)) {
        throw new IllegalArgumentException();
      }
      return key;
    } catch (IllegalArgumentException | NullPointerException exception) {
      // 키의 누락과 잘못된 형식을 같은 멱등 키 입력 오류로 처리한다.
      throw new ApiException(ApiErrorCode.SESSION_IDEMPOTENCY_REQUIRED);
    }
  }

  /** 문자열 목표 ID를 양수 숫자로 변환하고 잘못된 ID는 존재하지 않는 목표로 처리한다. */
  private long parseGoalId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) {
        throw new NumberFormatException();
      }
      return id;
    } catch (NumberFormatException exception) {
      // 잘못된 형식·범위와 조회할 수 없는 목표를 동일하게 처리한다.
      throw new ApiException(ApiErrorCode.GOAL_NOT_FOUND);
    }
  }

  /** 유효한 지역 시간대를 사용하고 없거나 유효하지 않으면 서울 시간대를 사용한다. */
  private ZoneId zone(String value) {
    return value != null && ZoneId.getAvailableZoneIds().contains(value)
        ? ZoneId.of(value) : DEFAULT_ZONE;
  }

  /** 기기와 요청 본문의 SHA-256 해시를 만들어 같은 멱등 요청인지 비교한다. */
  static String requestHash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      // 필수 해시 알고리즘이 없으면 안전하게 비교할 수 없으므로 내부 오류로 중단한다.
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
