package com.capstone.backend.auth.service;

import com.capstone.backend.auth.AccessJwtService;
import com.capstone.backend.auth.dto.TokenLifecycleDtos.TokenPair;
import com.capstone.backend.auth.persistence.RefreshTokenRecordEntity;
import com.capstone.backend.auth.persistence.RefreshTokenRecordRepository;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.security.TokenFamilyGuard;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 사용자 → family 잠금 순서로 재로그인·회전·로그아웃의 경쟁을 직렬화한다. */
@Service
public class TokenLifecycleService {
  private static final Duration REFRESH_LIFETIME = Duration.ofDays(30);
  private final AccessJwtService jwt;
  private final UserRepository users;
  private final UserDeviceRepository devices;
  private final TokenFamilyRepository families;
  private final RefreshTokenRecordRepository tokens;
  private final TokenFamilyGuard guard;
  private final AuthRateLimiter limits;
  private final TransactionTemplate transaction;

  /** 기존 인증 저장소와 잠금·회전 transaction을 연결한다. */
  public TokenLifecycleService(AccessJwtService jwt, UserRepository users,
      UserDeviceRepository devices, TokenFamilyRepository families,
      RefreshTokenRecordRepository tokens, TokenFamilyGuard guard, AuthRateLimiter limits,
      PlatformTransactionManager transactions) {
    this.jwt = jwt;
    this.users = users;
    this.devices = devices;
    this.families = families;
    this.tokens = tokens;
    this.guard = guard;
    this.limits = limits;
    this.transaction = new TransactionTemplate(transactions);
  }

  /** 재사용 감지의 폐기를 먼저 commit한 다음 HTTP 계층에 401을 전달한다. */
  public TokenPair refresh(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    String hash = TokenSecrets.hash(rawToken);
    var context = tokens.findContextByHash(hash)
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    limits.check("api:" + context.getUserId() + ":" + context.getDeviceId(),
        60, Duration.ofMinutes(1));
    RefreshOutcome outcome = transaction.execute(status -> rotate(context, hash));
    if (outcome == null) {
      throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
    }
    if (outcome.error() != null) {
      throw new ApiException(outcome.error());
    }
    return outcome.pair();
  }

  /** 잠금 전 조회한 것은 ID뿐이다. 회전 여부는 반드시 잠금 뒤 다시 읽는다. */
  private RefreshOutcome rotate(RefreshTokenRecordRepository.TokenContext context, String hash) {
    var user = users.findByIdForUpdate(context.getUserId()).orElse(null);
    if (user == null || user.getDeletedAt() != null) {
      return RefreshOutcome.relogin();
    }
    var family = families.findByIdForUpdate(context.getFamilyId()).orElse(null);
    var previous = tokens.findByTokenHash(hash).orElse(null);
    if (family == null || previous == null
        || !family.getUser().getId().equals(context.getUserId())
        || !family.getDevice().getId().equals(context.getDeviceId())
        || !previous.getTokenFamily().getId().equals(family.getId())) {
      return RefreshOutcome.relogin();
    }
    Instant now = Instant.now();
    if (previous.getStatus() == RefreshTokenRecordEntity.Status.ROTATED
        || previous.getStatus() == RefreshTokenRecordEntity.Status.REVOKED) {
      revokeFamily(family, TokenFamilyEntity.RevokedReason.TOKEN_REUSE, now);
      return new RefreshOutcome(null, ApiErrorCode.AUTH_TOKEN_REUSE_DETECTED);
    }
    if (previous.getStatus() != RefreshTokenRecordEntity.Status.ACTIVE
        || !previous.getExpiresAt().isAfter(now) || !guard.isActive(family, now)
        || !guard.hasActiveLinkedDesktop(family.getDevice(), user.getId(), now)) {
      return RefreshOutcome.relogin();
    }
    previous.setStatus(RefreshTokenRecordEntity.Status.ROTATED);
    previous.setUsedAt(now);
    // PostgreSQL의 family당 ACTIVE 1개 partial UNIQUE를 만족하도록 먼저 반영한다.
    tokens.saveAndFlush(previous);
    Instant expiresAt = now.plus(REFRESH_LIFETIME);
    family.setExpiresAt(expiresAt);
    family.getDevice().setLastUsedAt(now);
    String rawNext = TokenSecrets.newRefreshToken();
    var next = new RefreshTokenRecordEntity();
    next.setTokenFamily(family);
    next.setParentToken(previous);
    next.setTokenHash(TokenSecrets.hash(rawNext));
    next.setExpiresAt(expiresAt);
    tokens.saveAndFlush(next);
    var access = jwt.issue(user.getId(), family.getDevice().getId(), family.getId());
    return new RefreshOutcome(new TokenPair(access.token(), rawNext, access.expiresAt()), null);
  }

  /** 현재 Desktop의 로그인만 끝내고 직접 연결된 Extension을 함께 폐기한다. */
  public void logout(FocusOnPrincipal principal) {
    if (principal.deviceType() != UserDeviceEntity.DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    transaction.executeWithoutResult(status -> {
      var user = users.findByIdForUpdate(principal.userId())
          .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
      var family = families.findByIdForUpdate(principal.tokenFamilyId())
          .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
      Instant now = Instant.now();
      if (!family.getUser().getId().equals(user.getId())
          || !family.getDevice().getId().equals(principal.deviceId())
          || family.getDevice().getDeviceType() != UserDeviceEntity.DeviceType.DESKTOP
          || !guard.isActive(family, now)) {
        throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
      }
      revokeFamily(family, TokenFamilyEntity.RevokedReason.LOGOUT, now);
      family.getDevice().setStatus(UserDeviceEntity.Status.LOGGED_OUT);
      family.getDevice().setRevokedAt(now);
      for (var extension : devices.findByUserIdAndLinkedDesktopDeviceIdAndDeviceType(
          user.getId(), principal.deviceId(), UserDeviceEntity.DeviceType.CHROME_EXTENSION)) {
        families.findByDeviceAndStatus(extension.getId(), TokenFamilyEntity.Status.ACTIVE)
            .ifPresent(child -> revokeFamily(child, TokenFamilyEntity.RevokedReason.LOGOUT, now));
        extension.setStatus(UserDeviceEntity.Status.REVOKED);
        extension.setRevokedAt(now);
      }
    });
  }

  /** 기존 폐기 사유는 보존하고 아직 사용 가능한 Refresh Token만 무효화한다. */
  private void revokeFamily(TokenFamilyEntity family, TokenFamilyEntity.RevokedReason reason,
      Instant now) {
    if (family.getStatus() != TokenFamilyEntity.Status.REVOKED) {
      family.setStatus(TokenFamilyEntity.Status.REVOKED);
      family.setRevokedReason(reason);
      family.setRevokedAt(now);
      families.saveAndFlush(family);
    }
    tokens.revokeActiveByFamily(family.getId(), now);
  }

  private record RefreshOutcome(TokenPair pair, ApiErrorCode error) {
    /** 폐기 등 transaction 결과를 보존한 채 외부에서 재로그인 오류를 반환한다. */
    private static RefreshOutcome relogin() {
      return new RefreshOutcome(null, ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
  }
}
