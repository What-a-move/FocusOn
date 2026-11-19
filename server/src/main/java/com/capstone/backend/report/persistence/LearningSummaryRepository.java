package com.capstone.backend.report.persistence;

import com.capstone.backend.report.dto.LearningSummaryResponse.Status;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 생성자가 저장한 세션별 단일 정리를 읽는다. */
public interface LearningSummaryRepository extends JpaRepository<LearningSummaryEntity, Long> {
  /**
   * 본인 세션의 READY 정리를 종료 시각·세션 ID 내림차순으로 커서 이후부터 읽는다.
   * 결과 행은 {세션, 정리} 쌍이다.
   */
  @Query("select ls, s from LearningSessionEntity ls, LearningSummaryEntity s "
      + "where s.sessionId = ls.id and ls.user.id = :userId and s.status = :ready "
      + "and ls.endedAt is not null and (ls.endedAt < :endedAt "
      + "or (ls.endedAt = :endedAt and ls.id < :sessionId)) "
      + "order by ls.endedAt desc, ls.id desc")
  List<Object[]> findReadyPage(@Param("userId") Long userId, @Param("ready") Status ready,
      @Param("endedAt") Instant endedAt, @Param("sessionId") long sessionId, Pageable page);
}
