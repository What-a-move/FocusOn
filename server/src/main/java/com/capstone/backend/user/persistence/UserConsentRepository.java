package com.capstone.backend.user.persistence;

import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자별 동의 상태를 조회한다. */
public interface UserConsentRepository extends JpaRepository<UserConsentEntity, Long> {
  /** 사용자의 동의 상태를 문서 종류 순으로 조회한다. */
  List<UserConsentEntity> findAllByUserIdOrderByConsentType(Long userId);

  /** 사용자와 문서 종류가 일치하는 동의 기록을 조회한다. */
  Optional<UserConsentEntity> findByUserIdAndConsentType(Long userId, ConsentType consentType);
}
