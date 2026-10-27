package com.capstone.backend.auth.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사용자 소유 기기와 활성 Desktop 수를 조회한다. */
public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, Long> {
  /** 사용자와 설치 해시로 기존 기기를 찾는다. */
  @Query("select d from UserDeviceEntity d where d.user.id = :userId "
      + "and d.deviceType = :deviceType and d.installationIdHash = :hash")
  Optional<UserDeviceEntity> findByInstallation(
      @Param("userId") Long userId,
      @Param("deviceType") UserDeviceEntity.DeviceType deviceType,
      @Param("hash") String installationIdHash);

  /** 기기 한도 초과 시 사용자 본인의 해제 가능한 Desktop 목록만 반환한다. */
  List<UserDeviceEntity> findByUserIdAndDeviceTypeAndStatus(
      Long userId, UserDeviceEntity.DeviceType deviceType, UserDeviceEntity.Status status);

  /** 로그아웃 대상 Desktop에 직접 연결된 같은 사용자 Extension만 찾는다. */
  List<UserDeviceEntity> findByUserIdAndLinkedDesktopDeviceIdAndDeviceType(
      Long userId, Long desktopId, UserDeviceEntity.DeviceType deviceType);

  /** 다른 계정의 활성 Extension 설치를 소유자 정보 노출 없이 검출한다. */
  @Query("select d.user.id from UserDeviceEntity d where d.installationIdHash = :hash "
      + "and d.deviceType = 'CHROME_EXTENSION' and d.status = 'ACTIVE'")
  Optional<Long> findActiveExtensionOwner(@Param("hash") String hash);

  /** 사용자별 특정 종류와 상태의 기기 수를 센다. */
  @Query("select count(d) from UserDeviceEntity d where d.user.id = :userId "
      + "and d.deviceType = :deviceType and d.status = :status")
  long countDevices(
      @Param("userId") Long userId,
      @Param("deviceType") UserDeviceEntity.DeviceType deviceType,
      @Param("status") UserDeviceEntity.Status status);
}
