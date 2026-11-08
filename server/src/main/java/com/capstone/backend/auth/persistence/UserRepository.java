package com.capstone.backend.auth.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 로그인 중 같은 사용자에게서 기기 수 제한을 직렬화할 수 있다. */
public interface UserRepository extends JpaRepository<UserEntity, Long> {
  /** 중복 닉네임을 확인한다. */
  Optional<UserEntity> findByNickname(String nickname);

  /** 활성 Desktop 3대 제한을 적용할 때 사용자 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserEntity u where u.id = :userId")
  Optional<UserEntity> findByIdForUpdate(@Param("userId") Long userId);
}
