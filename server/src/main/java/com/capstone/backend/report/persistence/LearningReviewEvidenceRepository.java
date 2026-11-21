package com.capstone.backend.report.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 입력 마감 전에 확정된 세션 근거만 읽는다. */
public interface LearningReviewEvidenceRepository
    extends JpaRepository<LearningReviewEvidenceEntity, Long> {
  /** 입력 마감 시각까지 생성된 근거를 관측 순서로 최대 페이지 크기만큼 읽는다. */
  @Query("select e from LearningReviewEvidenceEntity e where e.sessionId = :sessionId "
      + "and e.createdAt <= :cutoff order by e.observedAt, e.id")
  List<LearningReviewEvidenceEntity> findInput(@Param("sessionId") long sessionId,
      @Param("cutoff") Instant cutoff, Pageable page);
}
