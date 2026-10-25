package com.capstone.backend.auth.security;

import com.capstone.backend.auth.AccessJwtService;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서명 검증된 ID도 DB 소유·활성 상태를 통과해야 principal로 승격한다. */
@Service
public class RequestAuthenticationService {
  private final AccessJwtService tokens;
  private final TokenFamilyRepository families;

  /** 요청 토큰 검증기와 현재 family 저장소를 연결한다. */
  public RequestAuthenticationService(AccessJwtService tokens, TokenFamilyRepository families) {
    this.tokens = tokens;
    this.families = families;
  }

  /** family의 user·device FK를 따라 한 트랜잭션 안에서 현재 인증 상태를 검사한다. */
  @Transactional(readOnly = true)
  public FocusOnPrincipal authenticate(String token) {
    AccessJwtService.AccessTokenClaims claims;
    try {
      claims = tokens.verify(token);
    } catch (AccessJwtService.ExpiredAccessTokenException exception) {
      throw new ApiException(ApiErrorCode.AUTH_ACCESS_TOKEN_EXPIRED);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    TokenFamilyEntity family = families.findById(claims.tokenFamilyId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    var user = family.getUser();
    var device = family.getDevice();
    if (user.getId() != claims.userId() || device.getId() != claims.deviceId()
        || !device.getUser().getId().equals(user.getId()) || user.getDeletedAt() != null) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    if (device.getDeviceType() == UserDeviceEntity.DeviceType.CHROME_EXTENSION
        && (device.getStatus() != UserDeviceEntity.Status.ACTIVE
            || family.getStatus() == TokenFamilyEntity.Status.REVOKED)) {
      throw new ApiException(ApiErrorCode.LINK_TOKEN_REVOKED);
    }
    if (device.getStatus() != UserDeviceEntity.Status.ACTIVE
        || family.getStatus() != TokenFamilyEntity.Status.ACTIVE
        || !family.getExpiresAt().isAfter(Instant.now())) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    if (device.getDeviceType() == UserDeviceEntity.DeviceType.CHROME_EXTENSION) {
      var desktop = device.getLinkedDesktopDevice();
      if (desktop == null || !desktop.getUser().getId().equals(user.getId())
          || desktop.getDeviceType() != UserDeviceEntity.DeviceType.DESKTOP
          || desktop.getStatus() != UserDeviceEntity.Status.ACTIVE) {
        throw new ApiException(ApiErrorCode.LINK_TOKEN_REVOKED);
      }
    }
    return new FocusOnPrincipal(claims.userId(), claims.deviceId(), claims.tokenFamilyId(),
        device.getDeviceType());
  }
}
