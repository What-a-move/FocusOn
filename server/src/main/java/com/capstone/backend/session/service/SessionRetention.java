package com.capstone.backend.session.service;

import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.common.api.SafeApiLog;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 종료 후 1년이 지난 세션과 그 하위 기록을 지운다(DATA_PRIVACY: 세션 이력·요약·복습 노트·
 * 일시정지 구간·회고 = 세션 종료 후 1년). 목표·동의 이력·계정 설정은 세션 데이터가 아니므로
 * 남긴다. 세션 하나를 한 transaction으로 지워 중간에 멈춰도 다음 실행에서 이어진다.
 */
@Component
@RequiredArgsConstructor
public class SessionRetention {
  static final Duration RETENTION = Duration.ofDays(365);
  private static final int BATCH_SIZE = 100;
  private static final String TERMINAL = "('ENDED', 'ABANDONED')";
  private static final String CANDIDATES = "SELECT id, user_id FROM learning_sessions"
      + " WHERE status IN " + TERMINAL + " AND ended_at <= ? AND id > ?"
      + " ORDER BY id LIMIT " + BATCH_SIZE;
  private static final String LOCK_SESSION = "SELECT id FROM learning_sessions"
      + " WHERE id = ? AND status IN " + TERMINAL + " AND ended_at <= ? FOR UPDATE";
  /** 자식 → 부모 순서다. 모든 문장은 세션 ID 하나만 받는다. 순서는 탈퇴 계정 정리와 같다. */
  private static final List<String> DELETES = List.of(
      "DELETE FROM intervention_deliveries WHERE intervention_id IN "
          + "(SELECT id FROM intervention_events WHERE session_id = ?)",
      "DELETE FROM intervention_events WHERE session_id = ?",
      "DELETE FROM intervention_policy_states WHERE session_id = ?",
      "DELETE FROM learning_review_evidences WHERE session_id = ?",
      "DELETE FROM learning_summaries WHERE session_id = ?",
      "DELETE FROM analysis_runs WHERE session_id = ?",
      "DELETE FROM session_local_events WHERE session_id = ?",
      "DELETE FROM session_extensions WHERE session_id = ?",
      "DELETE FROM session_observation_contexts WHERE session_id = ?",
      "DELETE FROM session_goals WHERE session_id = ?",
      "DELETE FROM session_retrospectives WHERE session_id = ?",
      "DELETE FROM session_pauses WHERE session_id = ?",
      "DELETE FROM learning_sessions WHERE id = ?");

  private final Clock clock;
  private final JdbcTemplate jdbc;
  private final UserRepository users;
  private final TransactionTemplate transactions;

  /** 주기 실행 진입점이다. */
  @Scheduled(fixedDelayString = "${focuson.session.retention-delay-ms:3600000}",
      initialDelayString = "${focuson.session.retention-initial-delay-ms:120000}")
  public void cleanup() {
    purgeDue(clock.instant());
  }

  /** now 기준 보관 기한이 지난 세션을 정리하고 지운 세션 수를 돌려준다. */
  public synchronized int purgeDue(Instant now) {
    Timestamp cutoff = Timestamp.from(now.minus(RETENTION));
    int purged = 0;
    long afterId = 0;
    while (true) {
      List<Candidate> batch;
      try {
        batch = jdbc.query(CANDIDATES, (rs, row) -> new Candidate(rs.getLong("id"),
            rs.getLong("user_id")), cutoff, afterId);
      } catch (RuntimeException exception) {
        SafeApiLog.sessionRetentionFailure(exception);
        return purged;
      }
      if (batch.isEmpty()) {
        return purged;
      }
      for (Candidate candidate : batch) {
        try {
          if (Boolean.TRUE.equals(transactions.execute(status -> purge(candidate, cutoff)))) {
            purged++;
          }
        } catch (RuntimeException exception) {
          SafeApiLog.sessionRetentionFailure(exception);
        }
        afterId = candidate.sessionId();
      }
    }
  }

  private boolean purge(Candidate candidate, Timestamp cutoff) {
    // 다른 세션 작업과 같은 사용자 → 세션 잠금 순서를 따르고, 잠근 사용자는 다음 주기로 넘긴다.
    if (users.findByIdForUpdateSkipLocked(candidate.userId()).isEmpty()) {
      return false;
    }
    // 잠근 뒤 종료 상태와 기한을 다시 확인한다.
    if (jdbc.queryForList(LOCK_SESSION, Long.class, candidate.sessionId(), cutoff).isEmpty()) {
      return false;
    }
    for (String sql : DELETES) {
      jdbc.update(sql, candidate.sessionId());
    }
    return true;
  }

  private record Candidate(long sessionId, long userId) {}
}
