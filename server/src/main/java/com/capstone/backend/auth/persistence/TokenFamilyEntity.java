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

/** 기기별 Refresh Token 회전과 폐기의 단위다. */
@Entity
@Table(name = "token_families")
@Getter
@Setter
@NoArgsConstructor
public class TokenFamilyEntity {
  /** Token family의 수명 상태. */
  public enum Status {
    ACTIVE,
    REVOKED,
    EXPIRED
  }

  /** Token family를 폐기한 이유. */
  public enum RevokedReason {
    LOGOUT,
    UNLINK,
    TOKEN_REUSE,
    DEVICE_REVOKED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_id", nullable = false)
  private UserDeviceEntity device;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.ACTIVE;

  @Enumerated(EnumType.STRING)
  @Column(name = "revoked_reason", length = 32)
  private RevokedReason revokedReason;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
