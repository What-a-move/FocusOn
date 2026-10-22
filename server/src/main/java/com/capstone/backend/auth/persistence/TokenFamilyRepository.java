package com.capstone.backend.auth.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 기기당 활성 family를 찾고 토큰 회전 시 같은 family를 잠근다. */
public interface TokenFamilyRepository extends JpaRepository<TokenFamilyEntity, Long> {
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
