package com.capstone.backend.session.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자별 세션 명령의 멱등성 기록을 조회한다. */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, Long> {
  /** 사용자·명령·키로 기존 응답을 찾는다. */
  Optional<IdempotencyRecordEntity> findByUserIdAndScopeAndIdempotencyKey(
      Long userId, String scope, UUID idempotencyKey);
}
