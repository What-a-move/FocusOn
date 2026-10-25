package com.capstone.backend.auth.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Refresh Token 해시 조회로 회전·재사용 감지를 돕는다. */
public interface RefreshTokenRecordRepository
    extends JpaRepository<RefreshTokenRecordEntity, Long> {
  /** 동일 기기 재로그인 시 이전 family의 활성 Refresh Token만 폐기한다. */
  @Modifying
  @Query("update RefreshTokenRecordEntity r set r.status = "
      + "com.capstone.backend.auth.persistence.RefreshTokenRecordEntity.Status.REVOKED, "
      + "r.revokedAt = :now where r.tokenFamily.id = :familyId and r.status = "
      + "com.capstone.backend.auth.persistence.RefreshTokenRecordEntity.Status.ACTIVE")
  int revokeActiveByFamily(@Param("familyId") Long familyId, @Param("now") Instant now);

  /** 평문 대신 보관된 SHA-256 해시로 토큰 기록을 찾는다. */
  Optional<RefreshTokenRecordEntity> findByTokenHash(String tokenHash);
}
