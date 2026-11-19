package com.capstone.backend.report.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RELATED 화면에서 만든 학습 정리 근거다. 화면 원문·URL·제목은 두지 않고 120자 이하 개념만
 * 저장한다. 근거를 만드는 형식은 AI 팀과 합의 전이라 현재는 읽기 경로만 있다(#288).
 */
@Entity
@Table(name = "learning_review_evidences", uniqueConstraints = @UniqueConstraint(
    name = "uq_learning_review_evidence", columnNames = {"session_id", "evidence_hash"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningReviewEvidenceEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "session_id", nullable = false)
  private Long sessionId;
  @Column(name = "analysis_run_id", nullable = false)
  private Long analysisRunId;
  @Column(nullable = false, length = 120)
  private String concept;
  /** 같은 세션의 중복 근거를 막는 정규화 개념의 SHA-256 hex다. */
  @Column(name = "evidence_hash", nullable = false, length = 64)
  private String evidenceHash;
  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;
  @Column(name = "relation_confidence", precision = 4, scale = 3)
  private BigDecimal relationConfidence;
  /** 서버 수신 시각이다. 정리 입력 마감 비교에 쓴다. */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
