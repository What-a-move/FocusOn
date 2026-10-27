package com.capstone.backend.user.dto;

import com.capstone.backend.auth.persistence.UserEntity.OnboardingStatus;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserFocusSettingsEntity.InterventionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 사용자 설정 API의 요청·응답 DTO 모음이다. */
public final class UserConfigurationDtos {
  private UserConfigurationDtos() {}

  /** 현재 인증 사용자의 공개 정보다. */
  public record MeResponse(String userId, String nickname, OnboardingStatus onboardingStatus) {}

  /** 프로필 변경 요청이다. */
  public record UpdateProfileRequest(@NotBlank @Size(min = 2, max = 12) String nickname) {}

  /** 동의 변경 요청이다. */
  public record UpdateConsentRequest(@NotNull Boolean isGranted,
      @NotBlank @Size(max = 32) String consentVersion,
      @NotBlank @Size(max = 32) String consentSource) {}

  /** 동의 항목 응답이다. */
  public record ConsentResponse(ConsentType consentType, boolean isGranted, String consentVersion,
      Instant grantedAt, Instant withdrawnAt) {}

  /** 동의 조회 응답이다. */
  public record ConsentsResponse(String currentVersion, List<ConsentResponse> consents) {}

  /** 집중 설정 변경 요청이다. */
  public record UpdateFocusSettingsRequest(@NotNull InterventionLevel interventionLevel,
      @NotEmpty List<@NotBlank String> notificationChannels,
      @NotNull Boolean breakSuggestionEnabled, @NotNull Boolean cameraAnalysisEnabled) {}

  /** 집중 설정 응답이다. */
  public record FocusSettingsResponse(InterventionLevel interventionLevel,
      List<String> notificationChannels, boolean breakSuggestionEnabled,
      boolean cameraAnalysisEnabled) {}

  /** 분석 제외 도메인 전체 교체 요청이다. */
  public record ReplaceExcludedDomainsRequest(
      @NotNull @Size(max = 50) List<@NotBlank String> domains) {}

  /** 분석 제외 도메인 목록 응답이다. */
  public record ExcludedDomainsResponse(List<String> domains) {}
}
