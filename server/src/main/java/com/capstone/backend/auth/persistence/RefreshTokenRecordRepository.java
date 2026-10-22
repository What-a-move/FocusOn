package com.capstone.backend.auth.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Refresh Token 해시 조회로 회전·재사용 감지를 돕는다. */
public interface RefreshTokenRecordRepository
    extends JpaRepository<RefreshTokenRecordEntity, Long> {
  /** 평문 대신 보관된 SHA-256 해시로 토큰 기록을 찾는다. */
  Optional<RefreshTokenRecordEntity> findByTokenHash(String tokenHash);
}
