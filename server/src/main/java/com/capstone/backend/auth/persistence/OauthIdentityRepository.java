package com.capstone.backend.auth.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Google sub로 기존 계정을 찾는다. 이메일은 계정 키로 사용하지 않는다. */
public interface OauthIdentityRepository extends JpaRepository<OauthIdentityEntity, Long> {
  /** 제공자와 제공자 고유 식별자로 연결된 계정을 찾는다. */
  Optional<OauthIdentityEntity> findByProviderAndProviderSubject(
      OauthIdentityEntity.Provider provider, String providerSubject);
}
