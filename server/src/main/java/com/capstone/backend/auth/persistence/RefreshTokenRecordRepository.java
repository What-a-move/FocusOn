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

  /** 잠금 전에는 Entity를 올리지 않고 소유 문맥만 읽어 stale token 상태를 피한다. */
  @Query("select r.tokenFamily.user.id as userId, r.tokenFamily.device.id as deviceId, "
      + "r.tokenFamily.id as familyId from RefreshTokenRecordEntity r where r.tokenHash = :hash")
  Optional<TokenContext> findContextByHash(@Param("hash") String hash);

  /** 비밀값이 없는 잠금 순서 결정용 projection이다. */
  interface TokenContext {
    /** 첫 번째 잠금 대상인 사용자 ID를 반환한다. */
    Long getUserId();

    /** 잠금 뒤 family의 기기 소유 관계를 대조할 ID를 반환한다. */
    Long getDeviceId();

    /** 사용자 잠금 다음 획득할 family ID를 반환한다. */
    Long getFamilyId();
  }

  /** 평문 대신 보관된 SHA-256 해시로 토큰 기록을 찾는다. */
  Optional<RefreshTokenRecordEntity> findByTokenHash(String tokenHash);
}
