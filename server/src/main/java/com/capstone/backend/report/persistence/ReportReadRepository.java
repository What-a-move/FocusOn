package com.capstone.backend.report.persistence;

import com.capstone.backend.session.persistence.LearningSessionEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 보고서 조회에 필요한 종료 스냅샷만 읽으며 세션 상태를 변경하지 않는다. */
public interface ReportReadRepository extends Repository<LearningSessionEntity, Long> {
  /** 날짜는 현재 시간대가 아니라 세션 생성 시 고정한 학습 날짜다. */
  @Query("select s.studyDate as date, sum(s.accumulatedActiveMs) as totalActiveMs, "
      + "count(s) as sessionCount from LearningSessionEntity s "
      + "where s.user.id = :userId and s.studyDate >= :first and s.studyDate < :end "
      + "and s.status in (com.capstone.backend.session.persistence.SessionStatus.ENDED, "
      + "com.capstone.backend.session.persistence.SessionStatus.ABANDONED) "
      + "group by s.studyDate")
  List<DayTotals> summarize(@Param("userId") long userId, @Param("first") LocalDate first,
      @Param("end") LocalDate end);

  /** 목록과 합계의 원천을 한 번에 읽어 동시 종료 사이의 서로 다른 조회를 피한다. */
  @Query("select s.id as sessionId, s.goalTextSnapshot as goalTextSnapshot, "
      + "s.startedAt as startedAt, s.endedAt as endedAt, "
      + "s.accumulatedActiveMs as accumulatedActiveMs from LearningSessionEntity s "
      + "where s.user.id = :userId and s.studyDate = :date "
      + "and s.status in (com.capstone.backend.session.persistence.SessionStatus.ENDED, "
      + "com.capstone.backend.session.persistence.SessionStatus.ABANDONED)")
  List<SessionRow> findDaily(@Param("userId") long userId, @Param("date") LocalDate date);

  /** 기록이 존재하는 날짜의 실제 합계이며 빈 날짜의 표현은 API 계층에서 처리한다. */
  interface DayTotals {
    LocalDate getDate();

    long getTotalActiveMs();

    long getSessionCount();
  }

  /** 목표 테이블을 다시 읽지 않는 종료 당시 스냅샷이다. */
  interface SessionRow {
    Long getSessionId();

    String getGoalTextSnapshot();

    Instant getStartedAt();

    Instant getEndedAt();

    long getAccumulatedActiveMs();
  }
}
