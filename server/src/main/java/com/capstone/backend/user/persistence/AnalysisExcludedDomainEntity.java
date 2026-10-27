package com.capstone.backend.user.persistence;

import com.capstone.backend.auth.persistence.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 분석에서 제외할 사용자별 정규화 host다. */
@Entity
@Table(name = "user_analysis_excluded_domains")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisExcludedDomainEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(nullable = false, length = 253)
  private String host;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  private AnalysisExcludedDomainEntity(UserEntity user, String host, Instant createdAt) {
    this.user = user;
    this.host = host;
    this.createdAt = createdAt;
  }

  /** 정규화가 끝난 host를 저장 모델로 만든다. */
  public static AnalysisExcludedDomainEntity create(UserEntity user, String host,
      Instant createdAt) {
    return new AnalysisExcludedDomainEntity(user, host, createdAt);
  }
}
