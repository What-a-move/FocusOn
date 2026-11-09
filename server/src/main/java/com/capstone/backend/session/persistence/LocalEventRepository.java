package com.capstone.backend.session.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** 기기별 세션 이벤트 재전송을 판별한다. */
public interface LocalEventRepository extends JpaRepository<LocalEventEntity, Long> {
  /** 같은 기기가 보낸 동일 이벤트의 최초 수신 여부를 확인한다. */
  boolean existsBySessionIdAndDeviceIdAndEventHash(Long sessionId, Long deviceId, String hash);
}
