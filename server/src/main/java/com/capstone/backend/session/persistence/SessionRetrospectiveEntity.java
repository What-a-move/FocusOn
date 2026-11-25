package com.capstone.backend.session.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 끝난 세션의 한 줄 회고다(#318). 세션당 하나이며 다시 보내면 덮어쓴다. 메모는 로그에 남기지 않는다. */
@Entity
@Table(name = "session_retrospectives")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionRetrospectiveEntity {
  @Id
  @Column(name = "session_id")
  private Long sessionId;
  @Column(nullable = false, length = 8)
  private String rating;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reason_codes_json", nullable = false, columnDefinition = "jsonb")
  private String reasonCodesJson;
  @Column(length = 100)
  private String note;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** 검증을 마친 회고로 새 행을 만든다. */
  public static SessionRetrospectiveEntity create(long sessionId, String rating,
      String reasonCodesJson, String note, Instant now) {
    var entity = new SessionRetrospectiveEntity();
    entity.sessionId = sessionId;
    entity.createdAt = now;
    entity.replace(rating, reasonCodesJson, note, now);
    return entity;
  }

  /** 다시 보낸 회고로 통째 바꾼다. */
  public void replace(String rating, String reasonCodesJson, String note, Instant now) {
    this.rating = rating;
    this.reasonCodesJson = reasonCodesJson;
    this.note = note;
    this.updatedAt = now;
  }
}
