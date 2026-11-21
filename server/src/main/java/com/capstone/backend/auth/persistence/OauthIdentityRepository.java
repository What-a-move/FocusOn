package com.capstone.backend.auth.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Google sub로 기존 계정을 찾는다. 이메일은 계정 키로 사용하지 않는다. */
public interface OauthIdentityRepository extends JpaRepository<OauthIdentityEntity, Long> {
  /** 연결 해제되지 않은 제공자 계정만 찾는다. */
  Optional<OauthIdentityEntity> findByProviderAndProviderSubjectAndUnlinkedAtIsNull(
      OauthIdentityEntity.Provider provider, String providerSubject);

  /**
   * 계정 삭제 시 Google sub를 지우고 연결을 끊는다. (provider, sub) 전체 유일성을 지키면서
   * 같은 Google 계정의 다음 로그인이 새 계정을 만들 수 있게 행 ID 기반 값으로 바꾼다.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update OauthIdentityEntity i set i.unlinkedAt = :now, "
      + "i.providerSubject = concat('deleted:', cast(i.id as String)) "
      + "where i.user.id = :userId and i.unlinkedAt is null")
  int anonymizeForDeletedUser(@Param("userId") Long userId, @Param("now") Instant now);
}
