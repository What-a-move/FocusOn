package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.dto.AnalysisDtos.Response;
import com.capstone.backend.analysis.persistence.AnalysisRunEntity;
import com.capstone.backend.analysis.persistence.AnalysisRunRepository;
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
import com.capstone.backend.session.persistence.SessionStatus;
import com.capstone.backend.user.persistence.AnalysisExcludedDomainRepository;
import com.capstone.backend.user.persistence.UserConsentEntity;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserConsentRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI 호출 밖의 짧은 transaction 경계. 기존 사용자 잠금 순서를 유지한다. */
@Service
@RequiredArgsConstructor
public class AnalysisRunStore {
  private final UserRepository users;
  private final TokenFamilyRepository families;
  private final TokenFamilyGuard familyGuard;
  private final LearningSessionRepository sessions;
  private final AnalysisRunRepository runs;
  private final UserConsentRepository consents;
  private final AnalysisExcludedDomainRepository exclusions;
  private final AuthRateLimiter limits;

  /** 권한과 상태를 확인한 후 원문 없는 예약 행만 commit한다. */
  @Transactional
  public Prepared prepare(FocusOnPrincipal principal, long sessionId, Request request) {
    var device = lockAndValidate(principal);
    var session = ownedSession(principal, sessionId, device);
    if (session.getStatus() != SessionStatus.RUNNING) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    final Instant now = Instant.now();
    // observedAt은 기기 시각이다. 서버 startedAt/lastResumedAt/now와 직접 비교하지 않는다.
    limits.check("analysis:" + sessionId + ":" + principal.deviceId(), 8,
        Duration.ofMinutes(1));
    if (runs.existsBySessionIdAndClientRunId(sessionId, request.runUuid())) {
      throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
    }
    if (request.observationSequence() != null
        && runs.existsBySessionIdAndDeviceIdAndObservationSequence(sessionId, device.getId(),
            request.observationSequence().longValueExact())) {
      throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
    }
    var current = runs.findCurrent(sessionId, device.getId()).orElse(null);
    var source = runs.findFirstBySessionIdAndDeviceIdAndContentSourceOrderBySequenceDescIdDesc(
        sessionId, device.getId(), request.contentSource()).orElse(null);
    var order = AnalysisObservationOrder.classify(request, current, source);
    var run = AnalysisRunEntity.reserve(session, device, request, now);
    run.order(order.eligible(), order.untrusted());
    GuardStatus guard = guard(principal.userId(), request, session.getGoalTextSnapshot());
    if (order.untrusted()) {
      guard = AnalysisObservationOrder.retainGuard(guard, current);
    }
    if (guard != null) {
      run.complete(AnalysisDecision.guarded(guard));
    }
    runs.saveAndFlush(run);
    boolean latest = order.eligible() && !order.untrusted()
        && runs.findCurrent(sessionId, device.getId())
            .map(value -> value.getId().equals(run.getId())).orElse(false);
    return new Prepared(run.getId(), sessionId, session.getGoal().getId(),
        session.getVersion(), session.getLastResumedAt(), session.getGoalTextSnapshot(),
        session.getInterventionPolicyVersion(), guard, latest, run.response());
  }

  /** 호출 중의 unlink·동의 철회·세션 전이를 다시 확인하고 해당 이력만 완료한다. */
  @Transactional
  public Response complete(FocusOnPrincipal principal, Prepared prepared, Request request,
      AnalysisDecision decision) {
    var device = lockAndValidate(principal);
    var session = ownedSession(principal, prepared.sessionId(), device);
    var run = runs.findById(prepared.runId()).orElseThrow();
    var guard = guard(principal.userId(), request, session.getGoalTextSnapshot());
    if (guard != null) {
      decision = AnalysisDecision.guarded(guard);
    } else if (!prepared.latest() || run.isOrderingUntrusted() || !run.isCurrentEligible()
        || session.getStatus() != SessionStatus.RUNNING
        || !java.util.Objects.equals(session.getLastResumedAt(), prepared.runningSince())) {
      // pause/resume 동안 도착한 결과로 새 실행 구간을 판정하지 않는다.
      decision = AnalysisDecision.unavailable();
    }
    run.complete(decision);
    return run.response();
  }

  /** AI 호출 중 기기·family가 폐기될 수 있어 필터 인증과 별도로 잠금 아래 재검증한다. */
  private UserDeviceEntity lockAndValidate(FocusOnPrincipal principal) {
    users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    var family = families.findById(principal.tokenFamilyId())
        .filter(f -> f.getUser().getId().equals(principal.userId())
            && f.getDevice().getId().equals(principal.deviceId())
            && f.getDevice().getDeviceType() == principal.deviceType()
            && familyGuard.isActive(f, Instant.now())
            && familyGuard.hasActiveLinkedDesktop(f.getDevice(), principal.userId(), Instant.now()))
        .orElseThrow(() -> new ApiException(principal.deviceType()
            == UserDeviceEntity.DeviceType.CHROME_EXTENSION
            ? ApiErrorCode.LINK_TOKEN_REVOKED : ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    return family.getDevice();
  }

  /** 같은 계정의 다른 Desktop도 runner를 대신할 수 없으며 연결 Extension만 허용한다. */
  private LearningSessionEntity ownedSession(FocusOnPrincipal principal, long id,
      UserDeviceEntity device) {
    var session = sessions.findByIdAndUserId(id, principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
    Long desktopId = device.getDeviceType() == UserDeviceEntity.DeviceType.DESKTOP
        ? device.getId() : device.getLinkedDesktopDevice().getId();
    if (!session.getRunnerDevice().getId().equals(desktopId)) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
    return session;
  }

  /** 동의 철회·제외 목록 변경을 AI 전후에 확인해 오래된 허용 상태를 사용하지 않는다. */
  private GuardStatus guard(long userId, Request request, String goal) {
    if (request.guardStatus() == GuardStatus.PRIVACY_BLOCKED) {
      return GuardStatus.PRIVACY_BLOCKED;
    }
    for (var type : new ConsentType[] {ConsentType.TERMS_OF_SERVICE,
        ConsentType.PRIVACY_POLICY, ConsentType.SCREEN_ANALYSIS}) {
      if (!consents.findByUserIdAndConsentType(userId, type)
          .map(UserConsentEntity::isGranted).orElse(false)) {
        return GuardStatus.PRIVACY_BLOCKED;
      }
    }
    if (request.guardStatus() == GuardStatus.EXCLUDED) {
      return GuardStatus.EXCLUDED;
    }
    String host = AnalysisPrivacy.host(request);
    boolean excluded = !host.isEmpty() && exclusions.findAllByUserIdOrderByHost(userId).stream()
        .anyMatch(domain -> host.equals(domain.getHost())
            || host.endsWith("." + domain.getHost()));
    if (excluded) {
      return GuardStatus.EXCLUDED;
    }
    return AnalysisPrivacy.containsSensitive(request, goal) ? GuardStatus.PRIVACY_BLOCKED : null;
  }

  /** Entity나 원문을 transaction 밖으로 유출하지 않는 서버 snapshot이다. */
  public record Prepared(long runId, long sessionId, long goalId, long sessionVersion,
      Instant runningSince,
      String goalSnapshot, String policyVersion, GuardStatus guard, boolean latest,
      Response initialResponse) {
    @Override
    public String toString() {
      return "PreparedAnalysis[redacted]";
    }
  }
}
