package com.capstone.backend.user.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 기본으로 사용할 집중 설정이다. */
@Entity
@Table(name = "user_focus_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFocusSettingsEntity {
  /** 세션 개입 강도다. */
  public enum InterventionLevel { GENTLE, NORMAL, ACTIVE }

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "intervention_level", nullable = false, length = 16)
  private InterventionLevel interventionLevel;

  @Column(name = "notification_channels", nullable = false, length = 255)
  private String notificationChannels;

  @Column(name = "break_suggestion_enabled", nullable = false)
  private boolean breakSuggestionEnabled;

  @Column(name = "camera_analysis_enabled", nullable = false)
  private boolean cameraAnalysisEnabled;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private UserFocusSettingsEntity(long userId, InterventionLevel interventionLevel,
      String notificationChannels, boolean breakSuggestionEnabled, boolean cameraAnalysisEnabled,
      Instant now) {
    this.userId = userId;
    apply(interventionLevel, notificationChannels, breakSuggestionEnabled,
        cameraAnalysisEnabled, now);
  }

  /** 새 사용자의 기본 집중 설정을 만든다. */
  public static UserFocusSettingsEntity create(long userId, InterventionLevel interventionLevel,
      String notificationChannels, boolean breakSuggestionEnabled, boolean cameraAnalysisEnabled,
      Instant now) {
    return new UserFocusSettingsEntity(userId, interventionLevel, notificationChannels,
        breakSuggestionEnabled, cameraAnalysisEnabled, now);
  }

  /** 전체 집중 설정을 저장한다. */
  public void apply(InterventionLevel newInterventionLevel, String newNotificationChannels,
      boolean newBreakSuggestionEnabled, boolean newCameraAnalysisEnabled, Instant now) {
    interventionLevel = newInterventionLevel;
    notificationChannels = newNotificationChannels;
    breakSuggestionEnabled = newBreakSuggestionEnabled;
    cameraAnalysisEnabled = newCameraAnalysisEnabled;
    updatedAt = now;
  }
}
