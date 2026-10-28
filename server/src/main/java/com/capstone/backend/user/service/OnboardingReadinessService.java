package com.capstone.backend.user.service;

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
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserConsentRepository;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재 Desktop을 기준으로 서버가 확인한 온보딩 단계를 계산한다. */
@Service
public class OnboardingReadinessService {
  private final UserRepository users;
  private final UserDeviceRepository devices;
  private final TokenFamilyRepository families;
  private final TokenFamilyGuard guard;
  private final UserConsentRepository consents;

  /** 사용자·기기·동의 상태를 조회한다. */
  public OnboardingReadinessService(UserRepository users, UserDeviceRepository devices,
      TokenFamilyRepository families, TokenFamilyGuard guard, UserConsentRepository consents) {
    this.users = users;
    this.devices = devices;
    this.families = families;
    this.guard = guard;
    this.consents = consents;
  }

  /** 부분 입력은 지정한 값만 변경하며 생략된 필드는 보존한다. */
  public record Update(Boolean screenAnalysisReady, Boolean analysisExcludedDomainsStepCompleted) {}

  /** Desktop이 단계별 진행 상태로 사용하는 다섯 값이다. */
  public record Steps(boolean profileCompleted, boolean extensionLinked,
      boolean screenAnalysisReady, boolean requiredConsentsCompleted,
      boolean analysisExcludedDomainsStepCompleted) {}

  /** 프론트의 온보딩 재개 응답이다. */
  public record Readiness(UserEntity.OnboardingStatus onboardingStatus, String currentStep,
      Steps steps) {}

  /** Extension 인증 조회에서는 연결된 Desktop 기준으로 상태를 반환한다. */
  @Transactional
  public Readiness forPrincipal(FocusOnPrincipal principal) {
    if (principal.deviceType() == UserDeviceEntity.DeviceType.DESKTOP) {
      return get(principal, null);
    }
    var extension = devices.findById(principal.deviceId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    var desktop = extension.getLinkedDesktopDevice();
    if (desktop == null || !extension.getUser().getId().equals(principal.userId())) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    var family = families.findByDeviceAndStatus(desktop.getId(), TokenFamilyEntity.Status.ACTIVE)
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    return get(new FocusOnPrincipal(principal.userId(), desktop.getId(), family.getId(),
        UserDeviceEntity.DeviceType.DESKTOP), null);
  }

  /** 사용자 행을 잠가 기기 연결과 동의 갱신의 순서를 일치시킨다. */
  @Transactional
  public Readiness get(FocusOnPrincipal principal, Update update) {
    if (principal.deviceType() != UserDeviceEntity.DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    var user = users.findByIdForUpdate(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    var family = families.findByIdForUpdate(principal.tokenFamilyId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    Instant now = Instant.now();
    var device = family.getDevice();
    if (!family.getUser().getId().equals(user.getId())
        || !device.getId().equals(principal.deviceId())
        || device.getDeviceType() != UserDeviceEntity.DeviceType.DESKTOP
        || !guard.isActive(family, now)) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    if (update != null) {
      if (update.screenAnalysisReady() != null) {
        device.setScreenAnalysisReady(update.screenAnalysisReady());
      }
      if (update.analysisExcludedDomainsStepCompleted() != null) {
        device.setExcludedDomainsStepCompleted(update.analysisExcludedDomainsStepCompleted());
      }
    }
    boolean linked = devices.findByUserIdAndLinkedDesktopDeviceIdAndDeviceType(user.getId(),
        device.getId(), UserDeviceEntity.DeviceType.CHROME_EXTENSION).stream()
        .filter(d -> d.getStatus() == UserDeviceEntity.Status.ACTIVE)
        .anyMatch(d -> families.findByDeviceAndStatus(d.getId(), TokenFamilyEntity.Status.ACTIVE)
            .filter(f -> guard.isActive(f, now)
                && guard.hasActiveLinkedDesktop(d, user.getId(), now))
            .isPresent());
    Set<ConsentType> granted = consents.findAllByUserIdOrderByConsentType(user.getId()).stream()
        .filter(c -> c.isGranted()).map(c -> c.getConsentType()).collect(Collectors.toSet());
    boolean required = granted.containsAll(Set.of(ConsentType.TERMS_OF_SERVICE,
        ConsentType.PRIVACY_POLICY, ConsentType.SCREEN_ANALYSIS));
    var steps = new Steps(user.getNickname() != null && !user.getNickname().isBlank(), linked,
        device.isScreenAnalysisReady(), required, device.isExcludedDomainsStepCompleted());
    String current = !steps.profileCompleted() ? "NICKNAME" : !steps.extensionLinked() ? "EXTENSION"
        : !steps.screenAnalysisReady() || !steps.requiredConsentsCompleted()
        ? "PERMISSIONS_AND_CONSENTS" : !steps.analysisExcludedDomainsStepCompleted()
        ? "EXCLUDED_SITES" : "COMPLETED";
    var status = "COMPLETED".equals(current) ? UserEntity.OnboardingStatus.COMPLETED
        : UserEntity.OnboardingStatus.INCOMPLETE;
    return new Readiness(status, current, steps);
  }
}
