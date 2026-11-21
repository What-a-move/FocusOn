package com.capstone.backend.report.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * RELATED 화면에서 AI가 만든 최소 복습 근거다. 원문·URL·코드·개인정보를 담지 않고
 * 120자 이하 개념 라벨만 둔다. 세션별 해시로 중복을 막는다.
 */
@Entity
@Table(name = "learning_review_evidences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningReviewEvidenceEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "session_id", nullable = false)
  private Long sessionId;
  @Column(name = "concept_label", nullable = false, length = 120)
  private String conceptLabel;
  @Column(name = "evidence_type", nullable = false, length = 32)
  private String evidenceType;
  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;
  @Column(name = "content_source", nullable = false, length = 32)
  private String contentSource;
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "dedupe_hash", nullable = false, length = 64, columnDefinition = "char(64)")
  private String dedupeHash;
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** 검증을 마친 근거 한 건을 만든다. */
  public static LearningReviewEvidenceEntity of(long sessionId, String conceptLabel,
      String evidenceType, Instant observedAt, String contentSource, String dedupeHash,
      Instant now) {
    var entity = new LearningReviewEvidenceEntity();
    entity.sessionId = sessionId;
    entity.conceptLabel = conceptLabel;
    entity.evidenceType = evidenceType;
    entity.observedAt = observedAt;
    entity.contentSource = contentSource;
    entity.dedupeHash = dedupeHash;
    entity.createdAt = now;
    return entity;
  }
}
