package com.capstone.backend.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 설치별 입력 잠금과 전역 설치 연결의 직렬화 지점이다. */
@Entity
@Table(name = "pairing_installation_attempts")
@Getter
@Setter
@NoArgsConstructor
public class PairingInstallationAttemptEntity {
  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "installation_id_hash", length = 64, columnDefinition = "char(64)")
  private String installationIdHash;
  @Column(name = "failed_attempts", nullable = false)
  private short failedAttempts;
  @Column(name = "locked_until")
  private Instant lockedUntil;
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
