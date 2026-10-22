package com.capstone.backend.auth.persistence;

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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Google 계정의 변경되지 않는 sub를 사용자와 연결한다. Google 토큰은 저장하지 않는다. */
@Entity
@Table(name = "oauth_identities")
@Getter
@Setter
@NoArgsConstructor
public class OauthIdentityEntity {
  /** MVP에서 허용한 OAuth 제공자. */
  public enum Provider {
    GOOGLE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Provider provider = Provider.GOOGLE;

  @Column(name = "provider_subject", nullable = false, length = 255)
  private String providerSubject;

  @CreationTimestamp
  @Column(name = "linked_at", nullable = false, updatable = false)
  private Instant linkedAt;

  @Column(name = "unlinked_at")
  private Instant unlinkedAt;
}
