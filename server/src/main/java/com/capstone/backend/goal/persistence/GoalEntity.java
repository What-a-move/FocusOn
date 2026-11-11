package com.capstone.backend.goal.persistence;

import com.capstone.backend.auth.persistence.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 확정한 학습 목표다. */
@Entity
@Table(name = "goals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalEntity {
  /** 목표 확정 경로다. */
  public enum ConfirmationSource { AI_CONFIRMED, MANUAL_CONFIRMED }

  /** 목표의 보관 상태다. */
  public enum Status { ACTIVE, ARCHIVED, DELETED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @ManyToOne
  @JoinColumn(name = "clarification_session_id")
  private GoalClarificationSession clarificationSession;

  @Column(name = "goal_text", nullable = false, length = 40)
  private String goalText;

  @Column(name = "normalized_goal_text", length = 200)
  private String normalizedGoalText;

  @Enumerated(EnumType.STRING)
  @Column(name = "confirmation_source", nullable = false, length = 32)
  private ConfirmationSource confirmationSource;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private GoalEntity(UserEntity user, String goalText, ConfirmationSource confirmationSource,
      Instant now) {
    this.user = user;
    this.goalText = goalText;
    this.confirmationSource = confirmationSource;
    this.status = Status.ACTIVE;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** 확정된 목표를 생성한다. */
  public static GoalEntity create(UserEntity user, String goalText,
      ConfirmationSource confirmationSource, Instant now) {
    return new GoalEntity(user, goalText, confirmationSource, now);
  }

  /** 검증을 마친 명확화 이력을 목표와 연결한다. */
  public void linkClarification(GoalClarificationSession session) {
    this.clarificationSession = session;
  }

  /** 수정된 목표 문구와 변경 시각을 반영한다. */
  public void updateText(String text, Instant now) {
    this.goalText = text;
    this.normalizedGoalText = null;
    this.updatedAt = now;
  }

  /** 이력을 보존하기 위해 목표를 논리 삭제한다. */
  public void delete(Instant now) {
    this.status = Status.DELETED;
    this.deletedAt = now;
    this.updatedAt = now;
  }
}
