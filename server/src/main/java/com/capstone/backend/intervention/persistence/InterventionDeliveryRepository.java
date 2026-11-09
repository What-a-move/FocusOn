package com.capstone.backend.intervention.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자→세션 잠금으로 receipt와 ack/생성을 함께 직렬화한다. */
public interface InterventionDeliveryRepository
    extends JpaRepository<InterventionDeliveryEntity, Long> {
  /** 같은 nonce 재시도의 최초 응답을 찾는다. */
  Optional<InterventionDeliveryEntity> findByInterventionIdAndClientDeliveryId(
      Long interventionId, UUID nonce);

  /** UNKNOWN은 후속 NOT_SENT만으로 지우지 않는다. */
  List<InterventionDeliveryEntity> findByInterventionIdOrderByIdAsc(Long interventionId);
}
