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

  /** 하루 목표 기본값(60분)이다. 설정이 없거나 옛 세션일 때도 쓴다(#373). */
  public static final long DEFAULT_DAILY_TARGET_MS = 3_600_000L;

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

  /** 학습에 쓰는 메신저 목록(쉼표 구분, 비면 없음)이다(#373). */
  @Column(name = "learning_messengers", nullable = false, length = 64)
  private String learningMessengers = "";

  @Column(name = "pre_distraction_reminder_enabled", nullable = false)
  private boolean preDistractionReminderEnabled = true;

  @Column(name = "daily_target_ms", nullable = false)
  private long dailyTargetMs = DEFAULT_DAILY_TARGET_MS;

  /** 주간 목표다. null이면 설정하지 않은 것이다. */
  @Column(name = "weekly_target_ms")
  private Long weeklyTargetMs;

  @Column(name = "retrospective_enabled", nullable = false)
  private boolean retrospectiveEnabled = true;

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

  /** 재설계 1차 설정을 저장한다(#373). 호출 쪽이 유지할 값은 현재 값으로 넘긴다. */
  public void applyRedesign(String newLearningMessengers, boolean newPreDistractionReminder,
      long newDailyTargetMs, Long newWeeklyTargetMs, boolean newRetrospectiveEnabled,
      Instant now) {
    learningMessengers = newLearningMessengers;
    preDistractionReminderEnabled = newPreDistractionReminder;
    dailyTargetMs = newDailyTargetMs;
    weeklyTargetMs = newWeeklyTargetMs;
    retrospectiveEnabled = newRetrospectiveEnabled;
    updatedAt = now;
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
