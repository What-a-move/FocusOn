package com.capstone.backend.intervention.dto;

import com.capstone.backend.session.dto.SessionDtos.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 기존 Desktop 조회/사용자 반응 계약이다. 전송 영수증과 구분한다. */
public final class InterventionDtos {
  private InterventionDtos() {}

  /** 기존 공개 개입 종류다. 이번 생성 대상은 OFF_TASK뿐이다. */
  public enum Type { OFF_TASK, BREAK_SUGGESTION }

  /** 전송 여부를 표현하는 enum이 아닌 사용자 반응/수명 상태다. */
  public enum Status { PENDING, ACKNOWLEDGED, DISMISSED, EXPIRED }

  /** 새 전송 확인 action을 기존 사용자 반응에 섞지 않는다. */
  public enum Action { ACKNOWLEDGED, DISMISSED }

  /** 목록 항목은 공개 문자열 식별자와 시작 당시 정책을 포함한다. */
  public record Item(String interventionId, Type type, Status status,
      List<NotificationChannel> channels, Instant createdAt, String policyVersion,
      String observationContextId, long remainingTtlMs) {}

  /** OS 제출 결과와 사용자 반응은 별개의 상태다. */
  public enum DispatchStatus { SENT, NOT_SENT, UNKNOWN }

  /** 실제 성공 채널만 보내며 불명/미전송은 빈 배열이다. */
  public record Delivery(@NotNull @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-"
      + "[89ab][0-9a-f]{3}-[0-9a-f]{12}") String clientDeliveryId,
      @NotNull DispatchStatus dispatchStatus,
      @NotNull @Size(max = 3) List<@NotNull NotificationChannel> channels) {}

  /** 서버 수신 기준 최초 응답이며 재전송으로 연장하지 않는다. */
  public record DeliveryReceipt(DispatchStatus dispatchStatus, Instant acceptedAt,
      Instant cooldownUntil) {}

  /** 멱등 키를 추가하지 않는 기존 반응 요청이다. */
  public record Acknowledge(@NotNull Action action) {}

  /** 만료 이벤트에 사용자 반응 시각을 지어내지 않는다. */
  public record Acknowledgement(Status status, Instant actedAt) {}
}
