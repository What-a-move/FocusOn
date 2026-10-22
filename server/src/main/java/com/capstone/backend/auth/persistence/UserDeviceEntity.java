package com.capstone.backend.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Desktop 또는 Extension 설치 기록. 원본 installation ID는 저장하지 않는다. */
@Entity
@Table(name = "user_devices")
@Getter
@Setter
@NoArgsConstructor
public class UserDeviceEntity {
  /** 로그인 주체인 기기의 종류. */
  public enum DeviceType {
    DESKTOP,
    CHROME_EXTENSION
  }

  /** 기기의 서버 측 활성 상태. */
  public enum Status {
    ACTIVE,
    REVOKED,
    LOGGED_OUT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Enumerated(EnumType.STRING)
  @Column(name = "device_type", nullable = false, length = 32)
  private DeviceType deviceType;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "installation_id_hash", nullable = false, length = 64,
      columnDefinition = "char(64)")
  private String installationIdHash;

  @Column(name = "display_name", nullable = false, length = 80)
  private String displayName;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "linked_desktop_device_id")
  private UserDeviceEntity linkedDesktopDevice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.ACTIVE;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
