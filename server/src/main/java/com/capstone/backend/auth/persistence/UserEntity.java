package com.capstone.backend.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** FocusOn 사용자 계정. Google 식별자는 {@link OauthIdentityEntity}에 별도로 저장한다. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {
  /** 온보딩 완료 여부. */
  public enum OnboardingStatus {
    INCOMPLETE,
    COMPLETED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 12)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(name = "onboarding_status", nullable = false, length = 16)
  private OnboardingStatus onboardingStatus = OnboardingStatus.INCOMPLETE;

  @Column(name = "user_time_zone", nullable = false, length = 64)
  private String userTimeZone = "Asia/Seoul";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
