package com.capstone.backend.user.service;

import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.common.api.SafeApiLog;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 삭제된 계정의 학습 기록을 운영 DB에서 지운다(DATA_PRIVACY: 탈퇴 계정 30일 이내).
 * 삭제 후 유예(기본 1일)를 두어 진행 중이던 세션의 무신호 종료와 학습 정리 작업이 먼저 끝나게 한다.
 * 동의 이력(삭제 후 3년)과 익명화된 계정·Google 연결 행은 남기고, 기기·토큰·연결 코드는 각자의
 * 보관 기준을 따르므로 여기서 지우지 않는다. 사용자별 한 transaction이라 중간에 멈춰도 다음
 * 실행에서 이어진다.
 */
@Component
@EnableScheduling
public class DeletedAccountPurge {
  private static final int BATCH_SIZE = 20;
  private static final String SESSIONS = "SELECT id FROM learning_sessions WHERE user_id = ?";
  /** 자식 → 부모 순서다. 모든 문장은 사용자 ID 하나만 받는다. */
  private static final List<String> DELETES = List.of(
      "DELETE FROM intervention_deliveries WHERE intervention_id IN "
          + "(SELECT id FROM intervention_events WHERE session_id IN (" + SESSIONS + "))",
      "DELETE FROM intervention_events WHERE session_id IN (" + SESSIONS + ")",
      "DELETE FROM intervention_policy_states WHERE session_id IN (" + SESSIONS + ")",
      "DELETE FROM learning_review_evidences WHERE session_id IN (" + SESSIONS + ")",
      "DELETE FROM learning_summaries WHERE session_id IN (" + SESSIONS + ")",
      "DELETE FROM analysis_runs WHERE session_id IN (" + SESSIONS + ")",
      "DELETE FROM session_local_events WHERE session_id IN (" + SESSIONS + ")",
      "DELETE FROM session_extensions WHERE session_id IN (" + SESSIONS + ")",
      "DELETE FROM session_observation_contexts WHERE session_id IN (" + SESSIONS + ")",
      "DELETE FROM session_goals WHERE session_id IN (" + SESSIONS + ")",
      "DELETE FROM learning_sessions WHERE user_id = ?",
      "DELETE FROM goals WHERE user_id = ?",
      "DELETE FROM goal_clarification_sessions WHERE user_id = ?",
      "DELETE FROM idempotency_records WHERE user_id = ?",
      "DELETE FROM user_analysis_excluded_domains WHERE user_id = ?",
      "DELETE FROM user_focus_settings WHERE user_id = ?");
  /** 지울 학습 기록이 남은 삭제 계정만 고른다. 이미 정리한 계정은 다시 잡지 않는다. */
  private static final String CANDIDATES = "SELECT u.id FROM users u WHERE u.deleted_at IS NOT NULL"
      + " AND u.deleted_at <= ? AND u.id > ? AND ("
      + "EXISTS (SELECT 1 FROM learning_sessions s WHERE s.user_id = u.id)"
      + " OR EXISTS (SELECT 1 FROM goals g WHERE g.user_id = u.id)"
      + " OR EXISTS (SELECT 1 FROM goal_clarification_sessions c WHERE c.user_id = u.id)"
      + " OR EXISTS (SELECT 1 FROM idempotency_records r WHERE r.user_id = u.id)"
      + " OR EXISTS (SELECT 1 FROM user_analysis_excluded_domains d WHERE d.user_id = u.id)"
      + " OR EXISTS (SELECT 1 FROM user_focus_settings f WHERE f.user_id = u.id))"
      + " ORDER BY u.id LIMIT " + BATCH_SIZE;
  private final JdbcTemplate jdbc;
  private final UserRepository users;
  private final TransactionTemplate transactions;
  private final Duration grace;

  /** 유예 시간은 설정으로 조정한다. 30일 기준을 넘지 않게 상한을 둔다. */
  public DeletedAccountPurge(JdbcTemplate jdbc, UserRepository users,
      TransactionTemplate transactions,
      @Value("${focuson.account-purge.grace:PT24H}") Duration grace) {
    if (grace.isNegative() || grace.compareTo(Duration.ofDays(7)) > 0) {
      throw new IllegalArgumentException("account purge grace must be between 0 and 7 days");
    }
    this.jdbc = jdbc;
    this.users = users;
    this.transactions = transactions;
    this.grace = grace;
  }

  /** 주기 실행 진입점이다. */
  @Scheduled(fixedDelayString = "${focuson.account-purge.delay-ms:3600000}",
      initialDelayString = "${focuson.account-purge.initial-delay-ms:60000}")
  public void run() {
    purgeDue(Instant.now());
  }

  /** now 기준 유예가 지난 삭제 계정을 정리하고 처리한 계정 수를 돌려준다. */
  public int purgeDue(Instant now) {
    Timestamp deletedBefore = Timestamp.from(now.minus(grace));
    int purged = 0;
    long afterId = 0;
    while (true) {
      List<Long> ids = jdbc.queryForList(CANDIDATES, Long.class, deletedBefore, afterId);
      if (ids.isEmpty()) {
        return purged;
      }
      for (Long id : ids) {
        try {
          Boolean done = transactions.execute(status -> purge(id, deletedBefore));
          if (Boolean.TRUE.equals(done)) {
            purged++;
          }
        } catch (RuntimeException exception) {
          SafeApiLog.accountPurgeFailure(exception);
        }
        afterId = id;
      }
    }
  }

  private boolean purge(long userId, Timestamp deletedBefore) {
    // 다른 사용자 작업과 같은 사용자 행 잠금 순서를 따른다. 잠근 뒤 삭제 상태를 다시 확인한다.
    var user = users.findByIdForUpdate(userId).orElse(null);
    if (user == null || user.getDeletedAt() == null
        || user.getDeletedAt().isAfter(deletedBefore.toInstant())) {
      return false;
    }
    for (String sql : DELETES) {
      int params = (int) sql.chars().filter(character -> character == '?').count();
      jdbc.update(sql, java.util.Collections.nCopies(params, userId).toArray());
    }
    return true;
  }
}
