package com.capstone.backend.auth.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Google sub로 기존 계정을 찾는다. 이메일은 계정 키로 사용하지 않는다. */
public interface OauthIdentityRepository extends JpaRepository<OauthIdentityEntity, Long> {
  /** 연결 해제되지 않은 제공자 계정만 찾는다. */
  Optional<OauthIdentityEntity> findByProviderAndProviderSubjectAndUnlinkedAtIsNull(
      OauthIdentityEntity.Provider provider, String providerSubject);
}
