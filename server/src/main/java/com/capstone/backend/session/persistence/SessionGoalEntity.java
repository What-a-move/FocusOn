package com.capstone.backend.session.persistence;

import com.capstone.backend.goal.persistence.GoalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 세션 시작 때 고른 목표의 사본이다. 목표 문구·세분화가 나중에 바뀌어도 이 세션의 판정 기준은
 * 그대로다. 세션 하나에 최대 3개(position 1~3)를 담을 수 있다.
 */
@Entity
@Table(name = "session_goals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionGoalEntity {
  @EmbeddedId
  private Key id;
  @Column(name = "goal_id", nullable = false)
  private Long goalId;
  @Column(name = "goal_text_snapshot", nullable = false, length = 40)
  private String goalTextSnapshot;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "goal_profile_snapshot_json", columnDefinition = "jsonb")
  private String goalProfileSnapshotJson;

  /** 세션 시작 시점의 목표 문구와 세분화를 복사한다. */
  public static SessionGoalEntity snapshot(long sessionId, int position, GoalEntity goal) {
    if (position < 1 || position > 3) {
      throw new IllegalArgumentException("session goal position must be 1..3");
    }
    var entity = new SessionGoalEntity();
    entity.id = new Key(sessionId, (short) position);
    entity.goalId = goal.getId();
    entity.goalTextSnapshot = goal.getGoalText();
    entity.goalProfileSnapshotJson = goal.getProfileJson();
    return entity;
  }

  /** 세션 중 할 일 교체: 같은 자리를 새 할 일의 문구·세분화 사본으로 덮어쓴다(#320). */
  public void replaceWith(GoalEntity goal) {
    this.goalId = goal.getId();
    this.goalTextSnapshot = goal.getGoalText();
    this.goalProfileSnapshotJson = goal.getProfileJson();
  }

  /** 다중 목표 도입 전 세션의 최초 목표 사본을 복구한다. */
  public static SessionGoalEntity legacySnapshot(LearningSessionEntity session) {
    var entity = new SessionGoalEntity();
    entity.id = new Key(session.getId(), (short) 1);
    entity.goalId = session.getGoal().getId();
    entity.goalTextSnapshot = session.getGoalTextSnapshot();
    return entity;
  }

  /** 세션 안 순서로 목표 사본을 구분한다. */
  @Embeddable
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class Key implements Serializable {
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    @Column(name = "position", nullable = false)
    private Short position;

    Key(Long sessionId, Short position) {
      this.sessionId = sessionId;
      this.position = position;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key && java.util.Objects.equals(sessionId, key.sessionId)
          && java.util.Objects.equals(position, key.position);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(sessionId, position);
    }
  }
}
