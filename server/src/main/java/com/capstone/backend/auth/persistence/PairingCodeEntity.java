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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 원문 대신 keyed hash를 보관하는 일회용 Desktop 연결 코드다. */
@Entity
@Table(name = "pairing_codes")
@Getter
@Setter
@NoArgsConstructor
public class PairingCodeEntity {
  /** 실패 횟수는 코드가 아닌 설치 상태에 귀속한다. */
  public enum Status {
    ACTIVE, CONSUMED, EXPIRED, CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "issuer_desktop_device_id", nullable = false)
  private UserDeviceEntity issuerDesktopDevice;
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "code_hash", nullable = false, length = 64, columnDefinition = "char(64)")
  private String codeHash;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.ACTIVE;
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "consumed_by_extension_device_id")
  private UserDeviceEntity consumedByExtensionDevice;
  @Column(name = "consumed_at")
  private Instant consumedAt;
  @Column(name = "cancelled_at")
  private Instant cancelledAt;
  @Column(name = "terminal_at")
  private Instant terminalAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
