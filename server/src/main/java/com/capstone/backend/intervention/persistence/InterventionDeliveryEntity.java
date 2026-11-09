package com.capstone.backend.intervention.persistence;

import com.capstone.backend.intervention.dto.InterventionDtos.DispatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 채널 호출 결과만 저장하며 사용자 인지를 증명하지 않는다. */
@Entity
@Table(name = "intervention_deliveries", uniqueConstraints = @UniqueConstraint(
    name = "uq_delivery_nonce", columnNames = {"intervention_id", "client_delivery_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterventionDeliveryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "intervention_id", nullable = false)
  private Long interventionId;
  @Column(name = "client_delivery_id", nullable = false)
  private UUID clientDeliveryId;
  @Enumerated(EnumType.STRING)
  @Column(name = "dispatch_status", nullable = false, length = 16)
  private DispatchStatus dispatchStatus;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "channels_json", nullable = false, columnDefinition = "jsonb")
  private String channelsJson;
  @Column(name = "accepted_at", nullable = false)
  private Instant acceptedAt;
  @Column(name = "cooldown_until")
  private Instant cooldownUntil;

  /** 최초 응답을 저장해 재시도 시 시각을 연장하지 않는다. */
  public static InterventionDeliveryEntity create(long eventId, UUID nonce,
      DispatchStatus status, String channels, Instant now, Instant cooldown) {
    var value = new InterventionDeliveryEntity();
    value.interventionId = eventId;
    value.clientDeliveryId = nonce;
    value.dispatchStatus = status;
    value.channelsJson = channels;
    value.acceptedAt = now;
    value.cooldownUntil = cooldown;
    return value;
  }
}
