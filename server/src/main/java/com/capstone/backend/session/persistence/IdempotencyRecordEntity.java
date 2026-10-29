package com.capstone.backend.session.persistence;

import com.capstone.backend.auth.persistence.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 사용자의 세션 명령 결과를 재시도할 때 재사용한다. */
@Entity
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecordEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(nullable = false, length = 32)
  private String scope;

  @Column(name = "idempotency_key", nullable = false)
  private UUID idempotencyKey;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
  private String requestHash;

  @Column(name = "response_status", nullable = false)
  private short responseStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_body_json", nullable = false, columnDefinition = "jsonb")
  private String responseBodyJson;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  private IdempotencyRecordEntity(UserEntity user, String scope, UUID idempotencyKey,
      String requestHash, short responseStatus, String responseBodyJson, Instant createdAt,
      Instant expiresAt) {
    this.user = user;
    this.scope = scope;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.responseStatus = responseStatus;
    this.responseBodyJson = responseBodyJson;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  /** 확정된 응답과 요청 해시를 보관한다. */
  public static IdempotencyRecordEntity create(UserEntity user, String scope, UUID key,
      String requestHash, short status, String body, Instant createdAt, Instant expiresAt) {
    return new IdempotencyRecordEntity(user, scope, key, requestHash, status, body, createdAt,
        expiresAt);
  }
}
