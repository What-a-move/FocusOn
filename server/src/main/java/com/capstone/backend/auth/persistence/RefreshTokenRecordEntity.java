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

/** Refresh Token의 해시와 회전 이력만 보관한다. 토큰 원문은 저장하지 않는다. */
@Entity
@Table(name = "refresh_token_records")
@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenRecordEntity {
  /** 회전된 Refresh Token을 포함한 토큰 기록의 상태. */
  public enum Status {
    ACTIVE,
    ROTATED,
    REVOKED,
    EXPIRED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "token_family_id", nullable = false)
  private TokenFamilyEntity tokenFamily;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "token_hash", nullable = false, unique = true, length = 64,
      columnDefinition = "char(64)")
  private String tokenHash;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_token_id")
  private RefreshTokenRecordEntity parentToken;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.ACTIVE;

  @CreationTimestamp
  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;
}
