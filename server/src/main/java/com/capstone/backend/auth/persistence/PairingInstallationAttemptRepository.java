package com.capstone.backend.auth.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** installation hash별 실패 상태와 계정 간 동시 연결을 보호한다. */
public interface PairingInstallationAttemptRepository
    extends JpaRepository<PairingInstallationAttemptEntity, String> {
  /** 존재하는 설치 행을 입력 transaction의 첫 잠금으로 잡는다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from PairingInstallationAttemptEntity a where a.installationIdHash = :hash")
  Optional<PairingInstallationAttemptEntity> findForUpdate(@Param("hash") String hash);

  /** 미완료 1~2회는 보존하며 성공한 오래된 행·만료된 잠금만 삭제한다. */
  @Modifying
  @Query("delete from PairingInstallationAttemptEntity a where "
      + "(a.failedAttempts = 0 and a.updatedAt <= :cutoff) "
      + "or (a.lockedUntil is not null and a.lockedUntil <= :now)")
  int deleteResettable(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
