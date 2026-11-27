package com.capstone.backend.user.dto;

import com.capstone.backend.auth.persistence.UserEntity.OnboardingStatus;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.persistence.UserFocusSettingsEntity.InterventionLevel;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
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
  public record UpdateProfileRequest(
      @Schema(description = "한글·영문·숫자로 구성된 2~12자 닉네임", example = "집중러")
      String nickname) {}

  /** 동의 변경 요청이다. */
  public record UpdateConsentRequest(
      @Schema(example = "true") @NotNull Boolean isGranted,
      @Schema(description = "현재 동의 문서 버전", example = "2026-09-17")
      @NotBlank @Size(max = 32) String consentVersion,
      @Schema(description = "동의를 수집한 클라이언트", example = "DESKTOP_ONBOARDING")
      @NotBlank @Size(max = 32) String consentSource) {}

  /** 동의 항목 응답이다. */
  public record ConsentResponse(ConsentType consentType, boolean isGranted, String consentVersion,
      Instant grantedAt, Instant withdrawnAt) {}

  /** 동의 조회 응답이다. */
  public record ConsentsResponse(String currentVersion, List<ConsentResponse> consents) {}

  /** 집중 설정 변경 요청이다. */
  public record UpdateFocusSettingsRequest(
      @Schema(allowableValues = {"GENTLE", "NORMAL", "ACTIVE"}, example = "NORMAL")
      @NotNull InterventionLevel interventionLevel,
      @ArraySchema(arraySchema = @Schema(example = "[\"MOLE_UI\", \"SOUND\"]"),
          schema = @Schema(allowableValues = {"MOLE_UI", "MACOS_NOTIFICATION", "SOUND"},
              example = "MOLE_UI"))
      @NotEmpty List<@NotBlank String> notificationChannels,
      @Schema(example = "true") @NotNull Boolean breakSuggestionEnabled,
      @Schema(example = "false") @NotNull Boolean cameraAnalysisEnabled,
      @ArraySchema(arraySchema = @Schema(description = "학습에 쓰는 메신저 (선택)",
          example = "[\"DISCORD\"]"), schema = @Schema(allowableValues = {"DISCORD", "SLACK",
              "KAKAOTALK", "TEAMS", "OTHER"})) List<String> learningMessengers,
      @Schema(description = "평소 첫 이탈 3분 전 미리 알림 (선택)", example = "true")
      Boolean preDistractionReminderEnabled,
      @Schema(description = "하루 목표 ms, 10~600분·분 단위 (선택)", implementation = Long.class,
          example = "3600000")
      tools.jackson.databind.JsonNode dailyTargetMs,
      @Schema(description = "주간 목표 ms, 60~4200분·분 단위, null이면 미설정 (선택)",
          implementation = Long.class, nullable = true, example = "null")
      tools.jackson.databind.JsonNode weeklyTargetMs,
      @Schema(description = "세션 끝 한 줄 회고 표시 (선택)", example = "true")
      Boolean retrospectiveEnabled) {
    /** 기존 4개 필드만 보내는 요청이다. */
    public UpdateFocusSettingsRequest(InterventionLevel interventionLevel,
        List<String> notificationChannels, Boolean breakSuggestionEnabled,
        Boolean cameraAnalysisEnabled) {
      this(interventionLevel, notificationChannels, breakSuggestionEnabled,
          cameraAnalysisEnabled, null, null, null, null, null);
    }
  }

  /** 집중 설정 응답이다. 재설계 1차 필드(#373)는 기본값을 포함해 항상 담는다. */
  public record FocusSettingsResponse(InterventionLevel interventionLevel,
      List<String> notificationChannels, boolean breakSuggestionEnabled,
      boolean cameraAnalysisEnabled, List<String> learningMessengers,
      boolean preDistractionReminderEnabled, long dailyTargetMs, Long weeklyTargetMs,
      boolean retrospectiveEnabled) {}

  /** 분석 제외 도메인 전체 교체 요청이다. */
  public record ReplaceExcludedDomainsRequest(
      @ArraySchema(arraySchema = @Schema(example = "[\"youtube.com\", \"example.com\"]"),
          schema = @Schema(description = "scheme·port·path 없이 입력하는 host", example = "youtube.com"))
      @NotNull @Size(max = 50) List<@NotBlank String> domains) {}

  /** 분석 제외 도메인 목록 응답이다. */
  public record ExcludedDomainsResponse(List<String> domains) {}
}
