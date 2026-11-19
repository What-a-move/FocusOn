package com.capstone.backend.report.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 정리 입력으로 고정할 세션 근거를 읽는다. */
public interface LearningReviewEvidenceRepository
    extends JpaRepository<LearningReviewEvidenceEntity, Long> {
  /** 입력 마감 전에 받은 근거를 관측 순서로 최대 20개 읽는다(AI 입력 2,400자 상한). */
  List<LearningReviewEvidenceEntity>
      findTop20BySessionIdAndCreatedAtLessThanEqualOrderByObservedAtAscIdAsc(
          Long sessionId, Instant inputClosesAt);
}
