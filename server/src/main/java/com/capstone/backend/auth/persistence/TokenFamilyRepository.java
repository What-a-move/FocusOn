package com.capstone.backend.auth.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 기기당 활성 family를 찾고 토큰 회전 시 같은 family를 잠근다. */
public interface TokenFamilyRepository extends JpaRepository<TokenFamilyEntity, Long> {
  /** 보관 기한이 지난 패밀리의 잠금에 필요한 식별자만 페이지 단위로 읽는다. */
  @Query("select f.id as familyId, f.user.id as userId from TokenFamilyEntity f "
      + "where f.id > :afterId and (case when f.status = "
      + "com.capstone.backend.auth.persistence.TokenFamilyEntity.Status.REVOKED "
      + "and f.revokedAt < f.expiresAt then f.revokedAt else f.expiresAt end) <= :cutoff "
      + "order by f.id")
  List<RetentionCandidate> findRetentionCandidates(@Param("afterId") long afterId,
      @Param("cutoff") Instant cutoff, Pageable page);

  /** 사용자 행이 패밀리보다 먼저 잠기게 한다. */
  interface RetentionCandidate {
    Long getFamilyId();

    Long getUserId();
  }

  /** 기기의 현재 활성 family를 조회한다. */
  @Query("select f from TokenFamilyEntity f where f.device.id = :deviceId "
      + "and f.status = :status")
  Optional<TokenFamilyEntity> findByDeviceAndStatus(
      @Param("deviceId") Long deviceId, @Param("status") TokenFamilyEntity.Status status);

  /** 동시 Refresh Token 회전 작업에서 family 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select family from TokenFamilyEntity family where family.id = :familyId")
  Optional<TokenFamilyEntity> findByIdForUpdate(@Param("familyId") Long familyId);
}
