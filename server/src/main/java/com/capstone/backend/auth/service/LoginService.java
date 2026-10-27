package com.capstone.backend.auth.service;

import com.capstone.backend.auth.AccessJwtService;
import com.capstone.backend.auth.dto.AuthDtos.DeviceInfo;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.Identity;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.dto.AuthDtos.UserInfo;
import com.capstone.backend.auth.persistence.OauthIdentityEntity;
import com.capstone.backend.auth.persistence.OauthIdentityRepository;
import com.capstone.backend.auth.persistence.PairingCodeRepository;
import com.capstone.backend.auth.persistence.RefreshTokenRecordEntity;
import com.capstone.backend.auth.persistence.RefreshTokenRecordRepository;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Google 통신과 DB 트랜잭션을 분리하고 사용자 잠금 안에서 기기·family를 발급한다. */
@Service
public class LoginService {
  private final GoogleOauthClient google;
  private final AccessJwtService jwt;
  private final UserRepository users;
  private final OauthIdentityRepository identities;
  private final UserDeviceRepository devices;
  private final TokenFamilyRepository families;
  private final RefreshTokenRecordRepository refreshTokens;
  private final PairingCodeRepository pairingCodes;
  private final TransactionTemplate transaction;

  /** 기존 인증 저장 모델을 재사용한다. */
  public LoginService(GoogleOauthClient google, AccessJwtService jwt, UserRepository users,
      OauthIdentityRepository identities, UserDeviceRepository devices,
      TokenFamilyRepository families, RefreshTokenRecordRepository refreshTokens,
      PairingCodeRepository pairingCodes, PlatformTransactionManager transactions) {
    this.google = google;
    this.pairingCodes = pairingCodes;
    this.jwt = jwt;
    this.users = users;
    this.identities = identities;
    this.devices = devices;
    this.families = families;
    this.refreshTokens = refreshTokens;
    this.transaction = new TransactionTemplate(transactions);
  }

  /** 외부 인증을 먼저 끝낸 뒤 최초 계정 생성 경쟁만 별도 트랜잭션으로 재시도한다. */
  public LoginResponse exchange(GoogleExchangeRequest request) {
    RedirectUriValidator.validate(request.redirectUri());
    String subject = google.exchange(request);
    try {
      return transaction.execute(status -> issue(subject, request));
    } catch (DataIntegrityViolationException exception) {
      // 같은 sub의 동시 최초 생성이면 실패 트랜잭션의 고아 사용자도 함께 rollback된다.
      return transaction.execute(status -> {
        if (identities.findByProviderAndProviderSubjectAndUnlinkedAtIsNull(
            OauthIdentityEntity.Provider.GOOGLE, subject).isEmpty()) {
          throw new ApiException(ApiErrorCode.AUTH_OAUTH_REAUTH_REQUIRED);
        }
        return issue(subject, request);
      });
    }
  }

  private LoginResponse issue(String subject, GoogleExchangeRequest request) {
    OauthIdentityEntity identity = identities
        .findByProviderAndProviderSubjectAndUnlinkedAtIsNull(
            OauthIdentityEntity.Provider.GOOGLE, subject).orElseGet(() -> {
              UserEntity user = users.saveAndFlush(new UserEntity());
              OauthIdentityEntity created = new OauthIdentityEntity();
              created.setUser(user);
              created.setProviderSubject(subject);
              return identities.saveAndFlush(created);
            });
    UserEntity user = users.findByIdForUpdate(identity.getUser().getId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    if (user.getDeletedAt() != null) {
      throw new ApiException(ApiErrorCode.AUTH_OAUTH_REAUTH_REQUIRED);
    }
    final Instant now = Instant.now();
    UserDeviceEntity device = devices.findByInstallation(user.getId(),
        UserDeviceEntity.DeviceType.DESKTOP, TokenSecrets.hash(request.device().installationId()))
        .orElseGet(UserDeviceEntity::new);
    if ((device.getId() == null || device.getStatus() != UserDeviceEntity.Status.ACTIVE)
        && devices.countDevices(user.getId(), UserDeviceEntity.DeviceType.DESKTOP,
            UserDeviceEntity.Status.ACTIVE) >= 3) {
      var available = devices.findByUserIdAndDeviceTypeAndStatus(user.getId(),
          UserDeviceEntity.DeviceType.DESKTOP, UserDeviceEntity.Status.ACTIVE).stream()
          .map(value -> new AvailableDevice(value.getId().toString(), value.getDisplayName(),
              value.getLastUsedAt())).toList();
      throw ApiException.withDetails(ApiErrorCode.AUTH_DEVICE_LIMIT_EXCEEDED,
          Map.of("devices", available));
    }
    device.setUser(user);
    device.setDeviceType(UserDeviceEntity.DeviceType.DESKTOP);
    device.setInstallationIdHash(TokenSecrets.hash(request.device().installationId()));
    device.setDisplayName(request.device().deviceName().trim());
    device.setStatus(UserDeviceEntity.Status.ACTIVE);
    device.setRevokedAt(null);
    device.setLastUsedAt(now);
    devices.saveAndFlush(device);
    families.findByDeviceAndStatus(device.getId(), TokenFamilyEntity.Status.ACTIVE)
        .ifPresent(old -> {
          old.setStatus(TokenFamilyEntity.Status.REVOKED);
          old.setRevokedReason(TokenFamilyEntity.RevokedReason.LOGOUT);
          old.setRevokedAt(now);
          families.saveAndFlush(old);
          refreshTokens.revokeActiveByFamily(old.getId(), now);
        });
    pairingCodes.cancelActive(device.getId(), now);
    TokenFamilyEntity family = new TokenFamilyEntity();
    family.setUser(user);
    family.setDevice(device);
    family.setExpiresAt(now.plus(Duration.ofDays(30)));
    families.saveAndFlush(family);
    String refresh = TokenSecrets.newRefreshToken();
    RefreshTokenRecordEntity record = new RefreshTokenRecordEntity();
    record.setTokenFamily(family);
    record.setTokenHash(TokenSecrets.hash(refresh));
    record.setExpiresAt(family.getExpiresAt());
    refreshTokens.saveAndFlush(record);
    var access = jwt.issue(user.getId(), device.getId(), family.getId());
    return new LoginResponse(access.token(), refresh, access.expiresAt(),
        userInfo(user), deviceInfo(device), user.getOnboardingStatus().name());
  }

  /** 요청 필터가 검증한 주체로 현재 사용자·기기 요약만 반환한다. */
  @Transactional(readOnly = true)
  public Identity me(FocusOnPrincipal principal) {
    UserEntity user = users.findById(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    UserDeviceEntity device = devices.findById(principal.deviceId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    return new Identity(userInfo(user), deviceInfo(device), user.getOnboardingStatus().name());
  }

  private static UserInfo userInfo(UserEntity user) {
    return new UserInfo(user.getId().toString(), user.getNickname());
  }

  private static DeviceInfo deviceInfo(UserDeviceEntity device) {
    String type = device.getDeviceType() == UserDeviceEntity.DeviceType.CHROME_EXTENSION
        ? "EXTENSION" : "DESKTOP";
    return new DeviceInfo(device.getId().toString(), type);
  }

  private record AvailableDevice(String deviceId, String deviceName, Instant lastUsedAt) {}
}
