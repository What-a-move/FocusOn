package com.capstone.backend.auth.service;

import com.capstone.backend.auth.AccessJwtService;
import com.capstone.backend.auth.dto.AuthDtos.DeviceInfo;
import com.capstone.backend.auth.dto.PairingDtos.ClaimRequest;
import com.capstone.backend.auth.dto.PairingDtos.IssuedCode;
import com.capstone.backend.auth.dto.PairingDtos.LinkedExtension;
import com.capstone.backend.auth.persistence.PairingCodeEntity;
import com.capstone.backend.auth.persistence.PairingCodeRepository;
import com.capstone.backend.auth.persistence.PairingInstallationAttemptEntity;
import com.capstone.backend.auth.persistence.PairingInstallationAttemptRepository;
import com.capstone.backend.auth.persistence.RefreshTokenRecordEntity;
import com.capstone.backend.auth.persistence.RefreshTokenRecordRepository;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.security.TokenFamilyGuard;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 설치 → 사용자 → 코드 순서로 잠가 입력·소비·재연결을 원자적으로 확정한다. */
@Service
public class PairingService {
  private final PairingCodeRepository codes;
  private final PairingInstallationAttemptRepository attempts;
  private final UserRepository users;
  private final UserDeviceRepository devices;
  private final TokenFamilyRepository families;
  private final RefreshTokenRecordRepository tokens;
  private final TokenFamilyGuard guard;
  private final AccessJwtService jwt;
  private final PairingSecrets secrets;
  private final AuthRateLimiter limits;
  private final Clock clock;
  private final EntityManager entities;
  private final TransactionTemplate transaction;
  private final TransactionTemplate creation;

  /** 기존 token 발급·guard와 독립 설치 잠금 저장소를 연결한다. */
  public PairingService(PairingCodeRepository codes,
      PairingInstallationAttemptRepository attempts, UserRepository users,
      UserDeviceRepository devices, TokenFamilyRepository families,
      RefreshTokenRecordRepository tokens, TokenFamilyGuard guard, AccessJwtService jwt,
      PairingSecrets secrets, AuthRateLimiter limits, Clock pairingClock, EntityManager entities,
      PlatformTransactionManager transactions) {
    this.codes = codes;
    this.attempts = attempts;
    this.users = users;
    this.devices = devices;
    this.families = families;
    this.tokens = tokens;
    this.guard = guard;
    this.jwt = jwt;
    this.secrets = secrets;
    this.limits = limits;
    this.clock = pairingClock;
    this.entities = entities;
    transaction = new TransactionTemplate(transactions);
    creation = new TransactionTemplate(transactions);
    creation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** 실패한 transaction 바깥에서 코드 hash 충돌만 제한 재시도한다. */
  public IssuedCode issue(FocusOnPrincipal principal) {
    requireDesktop(principal);
    limits.check("pairing-issue:" + principal.userId() + ":" + principal.deviceId(),
        3, Duration.ofMinutes(10));
    for (int retry = 0; retry < 3; retry++) {
      String raw = secrets.generate();
      try {
        return transaction.execute(status -> issueLocked(principal, raw));
      } catch (DataIntegrityViolationException exception) {
        if (!isUniqueCollision(exception, "uq_pairing_codes_hash")) {
          throw exception;
        }
      }
    }
    throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
  }

  private IssuedCode issueLocked(FocusOnPrincipal principal, String raw) {
    users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    Instant now = clock.instant();
    var desktop = currentDesktop(principal, now);
    codes.cancelActive(desktop.getId(), now);
    var code = new PairingCodeEntity();
    code.setUser(desktop.getUser());
    code.setIssuerDesktopDevice(desktop);
    code.setCodeHash(secrets.hash(raw));
    code.setCreatedAt(now);
    code.setExpiresAt(now.plusSeconds(300));
    codes.saveAndFlush(code);
    return new IssuedCode(code.getId().toString(), raw.substring(0, 3) + "-" + raw.substring(3),
        code.getExpiresAt(), 3);
  }

  /** 공개 입력의 한도를 먼저 검사하고 실패 카운터를 commit한 뒤 오류를 반환한다. */
  public LinkedExtension claim(ClaimRequest request, String remoteAddress) {
    limits.checkPairingInstallation(remoteAddress, request.installationId());
    String raw = secrets.normalize(request.pairingCode());
    String installationHash = TokenSecrets.hash(request.installationId());
    String codeHash = secrets.hash(raw);
    for (int retry = 0; retry < 3; retry++) {
      ensureAttemptRow(installationHash);
      ClaimOutcome result = transaction.execute(status -> claimLocked(installationHash, codeHash));
      // 정리 worker가 초기 행 생성 직후 삭제했을 때만 행을 다시 만들고 재시도한다.
      if (result != null && !result.missingAttempt()) {
        if (result.error() != null) {
          throw result.error();
        }
        return result.linked();
      }
    }
    throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
  }

  private void ensureAttemptRow(String hash) {
    try {
      creation.executeWithoutResult(status -> {
        if (!attempts.existsById(hash)) {
          var row = new PairingInstallationAttemptEntity();
          row.setInstallationIdHash(hash);
          row.setUpdatedAt(clock.instant());
          // assigned PK의 merge 대신 INSERT를 사용해 동시 생성 시 기존 카운터를 덮지 않는다.
          entities.persist(row);
          entities.flush();
        }
      });
    } catch (RuntimeException exception) {
      if (!isUniqueCollision(exception, null) || !attempts.existsById(hash)) {
        throw exception;
      }
    }
  }

  private ClaimOutcome claimLocked(String installationHash, String codeHash) {
    var attempt = attempts.findForUpdate(installationHash).orElse(null);
    if (attempt == null) {
      return new ClaimOutcome(null, null, true);
    }
    Instant now = clock.instant();
    if (attempt.getLockedUntil() != null && attempt.getLockedUntil().isAfter(now)) {
      return ClaimOutcome.error(locked(attempt.getLockedUntil(), now));
    }
    if (attempt.getLockedUntil() != null
        || (attempt.getFailedAttempts() > 0
            && !attempt.getUpdatedAt().plus(Duration.ofHours(24)).isAfter(now))) {
      reset(attempt, now);
    }
    var context = codes.findContext(codeHash).orElse(null);
    if (context == null) {
      return mismatch(attempt, now);
    }
    final UserEntity user = users.findByIdForUpdate(context.getUserId()).orElse(null);
    var code = codes.findForUpdate(context.getCodeId()).orElse(null);
    if (code == null) {
      return mismatch(attempt, clock.instant());
    }
    // 잠금 대기 동안 유효 기간이 지날 수 있으므로 획득 이후 시각으로 다시 검사한다.
    now = clock.instant();
    if (code.getStatus() == PairingCodeEntity.Status.ACTIVE
        && !code.getExpiresAt().isAfter(now)) {
      code.setStatus(PairingCodeEntity.Status.EXPIRED);
      code.setTerminalAt(code.getExpiresAt());
    }
    ApiErrorCode error = terminalError(code);
    if (error != null) {
      return ClaimOutcome.error(new ApiException(error));
    }
    var desktop = code.getIssuerDesktopDevice();
    var parent = families.findByDeviceAndStatus(desktop.getId(), TokenFamilyEntity.Status.ACTIVE)
        .orElse(null);
    if (user == null || user.getDeletedAt() != null || parent == null
        || desktop.getDeviceType() != UserDeviceEntity.DeviceType.DESKTOP
        || !desktop.getUser().getId().equals(user.getId()) || !guard.isActive(parent, now)) {
      code.setStatus(PairingCodeEntity.Status.CANCELLED);
      code.setCancelledAt(now);
      code.setTerminalAt(now);
      return ClaimOutcome.error(new ApiException(ApiErrorCode.LINK_005_CANCELLED));
    }
    var owner = devices.findActiveExtensionOwner(installationHash);
    if (owner.isPresent() && !owner.get().equals(user.getId())) {
      return ClaimOutcome.error(
          new ApiException(ApiErrorCode.LINK_007_INSTALLATION_ALREADY_LINKED));
    }
    LinkedExtension linked = link(user, desktop, installationHash, now);
    code.setStatus(PairingCodeEntity.Status.CONSUMED);
    code.setConsumedByExtensionDevice(
        devices.getReferenceById(Long.valueOf(linked.device().deviceId())));
    code.setConsumedAt(now);
    code.setTerminalAt(now);
    reset(attempt, now);
    codes.flush();
    return new ClaimOutcome(linked, null, false);
  }

  private LinkedExtension link(
      UserEntity user, UserDeviceEntity desktop, String hash, Instant now) {
    var device = devices.findByInstallation(user.getId(),
        UserDeviceEntity.DeviceType.CHROME_EXTENSION, hash).orElseGet(UserDeviceEntity::new);
    if (device.getId() != null) {
      families.findByDeviceAndStatus(device.getId(), TokenFamilyEntity.Status.ACTIVE)
          .ifPresent(old -> {
            old.setStatus(TokenFamilyEntity.Status.REVOKED);
            old.setRevokedReason(TokenFamilyEntity.RevokedReason.UNLINK);
            old.setRevokedAt(now);
            families.saveAndFlush(old);
            tokens.revokeActiveByFamily(old.getId(), now);
          });
    }
    device.setUser(user);
    device.setDeviceType(UserDeviceEntity.DeviceType.CHROME_EXTENSION);
    device.setInstallationIdHash(hash);
    device.setDisplayName("Chrome Extension");
    device.setLinkedDesktopDevice(desktop);
    device.setStatus(UserDeviceEntity.Status.ACTIVE);
    device.setRevokedAt(null);
    device.setLastUsedAt(now);
    devices.saveAndFlush(device);
    var family = new TokenFamilyEntity();
    family.setUser(user);
    family.setDevice(device);
    family.setExpiresAt(now.plus(Duration.ofDays(30)));
    families.saveAndFlush(family);
    String refresh = TokenSecrets.newRefreshToken();
    var record = new RefreshTokenRecordEntity();
    record.setTokenFamily(family);
    record.setTokenHash(TokenSecrets.hash(refresh));
    record.setExpiresAt(family.getExpiresAt());
    tokens.saveAndFlush(record);
    var access = jwt.issue(user.getId(), device.getId(), family.getId());
    return new LinkedExtension(access.token(), refresh, access.expiresAt(),
        new DeviceInfo(device.getId().toString(), "EXTENSION"));
  }

  /** 본인 Desktop 발급 ACTIVE 코드만 취소하며 다른 ID와 terminal은 동일한 204다. */
  public void cancel(FocusOnPrincipal principal, long codeId) {
    requireDesktop(principal);
    transaction.executeWithoutResult(status -> {
      users.findByIdForUpdate(principal.userId())
          .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
      Instant now = clock.instant();
      currentDesktop(principal, now);
      codes.findForUpdate(codeId).filter(code -> code.getUser().getId().equals(principal.userId())
          && code.getIssuerDesktopDevice().getId().equals(principal.deviceId())
          && code.getStatus() == PairingCodeEntity.Status.ACTIVE).ifPresent(code -> {
            if (!code.getExpiresAt().isAfter(now)) {
              code.setStatus(PairingCodeEntity.Status.EXPIRED);
              code.setTerminalAt(code.getExpiresAt());
            } else {
              code.setStatus(PairingCodeEntity.Status.CANCELLED);
              code.setCancelledAt(now);
              code.setTerminalAt(now);
            }
          });
    });
  }

  /** 사용자 잠금으로 연결·로그아웃·갱신과 직렬화하고 대상 family만 폐기한다. */
  public void unlink(FocusOnPrincipal principal, long extensionDeviceId) {
    transaction.executeWithoutResult(status -> {
      users.findByIdForUpdate(principal.userId())
          .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
      Instant now = clock.instant();
      var caller = families.findByIdForUpdate(principal.tokenFamilyId())
          .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
      if (!caller.getUser().getId().equals(principal.userId())
          || !caller.getDevice().getId().equals(principal.deviceId())
          || !guard.isActive(caller, now)
          || !guard.hasActiveLinkedDesktop(caller.getDevice(), principal.userId(), now)) {
        throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
      }
      var device = devices.findById(extensionDeviceId)
          .filter(d -> d.getUser().getId().equals(principal.userId()))
          .filter(d -> d.getDeviceType() == UserDeviceEntity.DeviceType.CHROME_EXTENSION)
          .filter(d -> principal.deviceType() == UserDeviceEntity.DeviceType.DESKTOP
              || d.getId().equals(principal.deviceId()))
          .orElseThrow(() -> new ApiException(ApiErrorCode.DEVICE_NOT_FOUND));
      families.findByDeviceAndStatus(device.getId(), TokenFamilyEntity.Status.ACTIVE)
          .ifPresent(family -> {
            var locked = families.findByIdForUpdate(family.getId()).orElseThrow();
            locked.setStatus(TokenFamilyEntity.Status.REVOKED);
            locked.setRevokedReason(TokenFamilyEntity.RevokedReason.UNLINK);
            locked.setRevokedAt(now);
            tokens.revokeActiveByFamily(locked.getId(), now);
          });
      device.setStatus(UserDeviceEntity.Status.REVOKED);
      device.setRevokedAt(now);
    });
  }

  private UserDeviceEntity currentDesktop(FocusOnPrincipal principal, Instant now) {
    var family = families.findByIdForUpdate(principal.tokenFamilyId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    if (!family.getUser().getId().equals(principal.userId())
        || !family.getDevice().getId().equals(principal.deviceId())
        || family.getDevice().getDeviceType() != UserDeviceEntity.DeviceType.DESKTOP
        || !guard.isActive(family, now)) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    return family.getDevice();
  }

  private static void requireDesktop(FocusOnPrincipal principal) {
    if (principal == null || principal.deviceType() != UserDeviceEntity.DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
  }

  private static ApiErrorCode terminalError(PairingCodeEntity code) {
    return switch (code.getStatus()) {
      case ACTIVE -> null;
      case EXPIRED -> ApiErrorCode.LINK_002_EXPIRED;
      case CONSUMED -> ApiErrorCode.LINK_003_ALREADY_USED;
      case CANCELLED -> ApiErrorCode.LINK_005_CANCELLED;
    };
  }

  private static ClaimOutcome mismatch(PairingInstallationAttemptEntity attempt, Instant now) {
    attempt.setFailedAttempts((short) (attempt.getFailedAttempts() + 1));
    attempt.setUpdatedAt(now);
    if (attempt.getFailedAttempts() == 3) {
      attempt.setLockedUntil(now.plusSeconds(180));
      return ClaimOutcome.error(ApiException.pairingLocked(180));
    }
    return ClaimOutcome.error(ApiException.withDetails(ApiErrorCode.LINK_006_CODE_MISMATCH,
        Map.of("remainingAttempts", 3 - attempt.getFailedAttempts())));
  }

  private static void reset(PairingInstallationAttemptEntity attempt, Instant now) {
    attempt.setFailedAttempts((short) 0);
    attempt.setLockedUntil(null);
    attempt.setUpdatedAt(now);
  }

  private static ApiException locked(Instant until, Instant now) {
    long millis = Duration.between(now, until).toMillis();
    return ApiException.pairingLocked(Math.max(1, (millis + 999) / 1000));
  }

  private static boolean isUniqueCollision(Throwable error, String constraint) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
          && (constraint == null || sql.getMessage().contains(constraint))) {
        return true;
      }
    }
    return false;
  }

  /** 코드·실패 상태 정리는 단일 테이블 조건부 변경만 하며 사용자 잠금을 역으로 잡지 않는다. */
  @Scheduled(fixedDelayString = "${focuson.pairing.cleanup-delay-ms:60000}", initialDelay = 60000)
  public void cleanup() {
    Instant now = clock.instant();
    transaction.executeWithoutResult(status -> {
      codes.expireActive(now);
      codes.deleteTerminalBefore(now.minus(Duration.ofDays(30)));
    });
    // claim은 설치 행 다음 코드를 잠근다. 역순 교착을 피하려고 별도 transaction으로 정리한다.
    transaction.executeWithoutResult(status ->
        attempts.deleteResettable(now.minus(Duration.ofDays(30)),
            now.minus(Duration.ofHours(24)), now));
  }

  private record ClaimOutcome(LinkedExtension linked, ApiException error, boolean missingAttempt) {
    private static ClaimOutcome error(ApiException error) {
      return new ClaimOutcome(null, error, false);
    }
  }
}
