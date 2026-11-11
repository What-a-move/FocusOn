package com.capstone.backend.goal.persistence;

import com.capstone.backend.auth.persistence.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 서버가 소유권과 대화 순서를 관리하는 목표 명확화 이력이다. */
@Entity
@Table(name = "goal_clarification_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalClarificationSession {
  /** 처리 중 중복 요청을 구분하고 확정된 이력의 재사용을 방지한다. */
  public enum Status { PROCESSING, READY, CONFIRMED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "goal_text", nullable = false, length = 40)
  private String goalText;

  @Column(name = "conversation_turn", nullable = false)
  private int conversationTurn;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "state_json", nullable = false, columnDefinition = "jsonb")
  private String stateJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** 최초 요청을 처리 중인 대화를 만들고 24시간의 유효기간을 정한다. */
  public static GoalClarificationSession create(UserEntity user, String text, Instant now) {
    GoalClarificationSession session = new GoalClarificationSession();
    session.user = user;
    session.goalText = text;
    session.stateJson = "{}";
    session.status = Status.PROCESSING;
    session.expiresAt = now.plus(Duration.ofHours(24));
    session.createdAt = now;
    session.updatedAt = now;
    return session;
  }

  /** 중복 요청이 같은 대화를 동시에 변경하지 못하도록 처리 상태로 전환한다. */
  public void begin(Instant now) {
    this.status = Status.PROCESSING;
    this.updatedAt = now;
  }

  /** 검증된 AI 응답과 사용자가 제공한 답변을 함께 보관한다. */
  public void complete(String state, int turn, Instant now) {
    this.stateJson = state;
    this.conversationTurn = turn;
    this.status = Status.READY;
    this.updatedAt = now;
  }

  /** 실패한 호출은 기존 이력을 유지한 채 재요청 가능하게 한다. */
  public void release(Instant now) {
    this.status = Status.READY;
    this.updatedAt = now;
  }

  /** 하나의 대화 이력은 하나의 목표 확정에만 사용한다. */
  public void confirm(Instant now) {
    this.status = Status.CONFIRMED;
    this.updatedAt = now;
  }
}
