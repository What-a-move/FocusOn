package com.capstone.backend.user.persistence;

import com.capstone.backend.auth.persistence.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자의 버전별 동의 상태를 저장한다. */
@Entity
@Table(name = "user_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsentEntity {
  /** 명세에 정의된 동의 문서 종류다. */
  public enum ConsentType {
    TERMS_OF_SERVICE, PRIVACY_POLICY, SCREEN_ANALYSIS, CAMERA_ANALYSIS
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Enumerated(EnumType.STRING)
  @Column(name = "consent_type", nullable = false, length = 32)
  private ConsentType consentType;

  @Column(name = "is_granted", nullable = false)
  private boolean granted;

  @Column(name = "consent_version", nullable = false, length = 32)
  private String consentVersion;

  @Column(name = "granted_at")
  private Instant grantedAt;

  @Column(name = "withdrawn_at")
  private Instant withdrawnAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private UserConsentEntity(UserEntity user, ConsentType consentType, boolean granted,
      String consentVersion, Instant now) {
    this.user = user;
    this.consentType = consentType;
    apply(granted, consentVersion, now);
  }

  /** 새 동의 기록을 만든다. */
  public static UserConsentEntity create(UserEntity user, ConsentType consentType, boolean granted,
      String consentVersion, Instant now) {
    return new UserConsentEntity(user, consentType, granted, consentVersion, now);
  }

  /** 현재 문서 버전의 동의 상태로 갱신한다. */
  public void apply(boolean newGranted, String newConsentVersion, Instant now) {
    granted = newGranted;
    consentVersion = newConsentVersion;
    grantedAt = newGranted ? now : null;
    withdrawnAt = newGranted ? null : now;
    updatedAt = now;
  }
}
