package com.capstone.backend.report.persistence;

import com.capstone.backend.report.dto.LearningSummaryResponse.Status;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사용자 소유 세션의 저장된 정리를 읽는다. */
public interface LearningSummaryRepository extends JpaRepository<LearningSummaryEntity, Long> {
  /** 정리 본문을 읽지 않고 종료된 세션의 목록에 필요한 필드만 반환한다. */
  @Query("select s.id as sessionId, s.goalTextSnapshot as goalSnapshot, s.endedAt as endedAt "
      + "from LearningSessionEntity s, LearningSummaryEntity n "
      + "where n.sessionId = s.id and s.user.id = :userId and s.status = :sessionStatus "
      + "and n.status = :summaryStatus order by s.endedAt desc, s.id desc")
  List<ListItem> findList(@Param("userId") long userId,
      @Param("sessionStatus") SessionStatus sessionStatus,
      @Param("summaryStatus") Status summaryStatus, Pageable pageable);

  /** 목록에 필요한 세션 메타데이터다. */
  interface ListItem {
    Long getSessionId();

    String getGoalSnapshot();

    Instant getEndedAt();
  }
}
