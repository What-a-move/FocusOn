package com.capstone.backend.auth.security;

import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** 요청 인증과 refresh가 같은 사용자·기기·연결 Desktop 기준을 사용하게 한다. */
@Component
public class TokenFamilyGuard {
  private final TokenFamilyRepository families;

  /** family 조회를 공유해 요청 인증과 갱신의 연결 상태 판정을 일치시킨다. */
  public TokenFamilyGuard(TokenFamilyRepository families) {
    this.families = families;
  }

  /** 호출자는 Entity 연관을 읽을 수 있는 transaction 안에서 사용한다. */
  public boolean isActive(TokenFamilyEntity family, Instant now) {
    var user = family.getUser();
    var device = family.getDevice();
    return user.getDeletedAt() == null && device.getUser().getId().equals(user.getId())
        && device.getStatus() == UserDeviceEntity.Status.ACTIVE
        && family.getStatus() == TokenFamilyEntity.Status.ACTIVE
        && family.getExpiresAt().isAfter(now);
  }

  /** 연결 Desktop의 기기 상태뿐 아니라 실제 로그인 family의 수명도 확인한다. */
  public boolean hasActiveLinkedDesktop(UserDeviceEntity device, long userId, Instant now) {
    if (device.getDeviceType() == UserDeviceEntity.DeviceType.DESKTOP) {
      return true;
    }
    var desktop = device.getLinkedDesktopDevice();
    if (desktop == null || !desktop.getUser().getId().equals(userId)
        || desktop.getDeviceType() != UserDeviceEntity.DeviceType.DESKTOP
        || desktop.getStatus() != UserDeviceEntity.Status.ACTIVE) {
      return false;
    }
    return families.findByDeviceAndStatus(desktop.getId(), TokenFamilyEntity.Status.ACTIVE)
        .filter(parent -> parent.getUser().getId().equals(userId) && isActive(parent, now))
        .isPresent();
  }
}
