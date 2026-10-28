package com.capstone.backend.auth.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 코드 선조회는 ID만 반환하고 상태 판단은 사용자 잠금 뒤 다시 읽는다. */
public interface PairingCodeRepository extends JpaRepository<PairingCodeEntity, Long> {
  /** 잠금 전 영속성 컨텍스트에 오래된 상태를 적재하지 않는다. */
  @Query("select p.id as codeId, p.user.id as userId from PairingCodeEntity p "
      + "where p.codeHash = :hash")
  Optional<CodeContext> findContext(@Param("hash") String hash);

  /** 사용자 잠금 뒤 코드 소비와 취소를 직렬화한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PairingCodeEntity p where p.id = :id")
  Optional<PairingCodeEntity> findForUpdate(@Param("id") Long id);

  /** 재발급·로그아웃·재로그인에서 이전 활성 코드만 원자적으로 취소한다. */
  default int cancelActive(Long deviceId, Instant now) {
    expireForDesktop(deviceId, now);
    return cancelUnexpired(deviceId, now);
  }

  /** 사용자 잠금 안에서 이미 만료된 행의 사유·보관 기준을 먼저 확정한다. */
  @Modifying
  @Query("update PairingCodeEntity p set p.status = 'EXPIRED', p.terminalAt = p.expiresAt "
      + "where p.issuerDesktopDevice.id = :deviceId and p.status = 'ACTIVE' "
      + "and p.expiresAt <= :now")
  int expireForDesktop(@Param("deviceId") Long deviceId, @Param("now") Instant now);

  /** 아직 유효한 코드만 취소하며 sweep 지연을 이유로 보관 기간을 늘리지 않는다. */
  @Modifying
  @Query("update PairingCodeEntity p set p.status = 'CANCELLED', p.cancelledAt = :now, "
      + "p.terminalAt = :now where p.issuerDesktopDevice.id = :deviceId and p.status = 'ACTIVE' "
      + "and p.expiresAt > :now")
  int cancelUnexpired(@Param("deviceId") Long deviceId, @Param("now") Instant now);

  /** worker 실행 지연으로 보관 기간이 늘어나지 않게 만료 시각을 사용한다. */
  @Modifying
  @Query("update PairingCodeEntity p set p.status = 'EXPIRED', p.terminalAt = p.expiresAt "
      + "where p.status = 'ACTIVE' and p.expiresAt <= :now")
  int expireActive(@Param("now") Instant now);

  /** terminal 코드의 확정 보관 기간만 삭제한다. */
  @Modifying
  @Query("delete from PairingCodeEntity p where p.terminalAt <= :cutoff "
      + "and p.status <> 'ACTIVE'")
  int deleteTerminalBefore(@Param("cutoff") Instant cutoff);

  /** 선조회에 필요한 식별자만 제공한다. */
  interface CodeContext {
    Long getCodeId();

    Long getUserId();
  }
}
