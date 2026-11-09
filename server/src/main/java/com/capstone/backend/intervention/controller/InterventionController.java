package com.capstone.backend.intervention.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiResponse;
import com.capstone.backend.intervention.dto.InterventionDtos.Acknowledge;
import com.capstone.backend.intervention.dto.InterventionDtos.Acknowledgement;
import com.capstone.backend.intervention.dto.InterventionDtos.Delivery;
import com.capstone.backend.intervention.dto.InterventionDtos.DeliveryReceipt;
import com.capstone.backend.intervention.dto.InterventionDtos.Item;
import com.capstone.backend.intervention.service.InterventionDeliveryService;
import com.capstone.backend.intervention.service.InterventionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Desktop이 사용하는 기존 두 공개 개입 API다. */
@RestController
@RequiredArgsConstructor
@Tag(name = "개입")
@SecurityRequirement(name = "focusOnBearer")
public class InterventionController {
  private final InterventionService interventions;
  private final InterventionDeliveryService deliveries;

  /** 조회는 전송/표시 영수증이 아니다. */
  @GetMapping("/api/v1/sessions/{sessionId}/interventions")
  @Operation(summary = "학습 세션 개입 조회")
  public ApiResponse<List<Item>> list(@AuthenticationPrincipal FocusOnPrincipal principal,
      @PathVariable String sessionId) {
    return ApiResponse.of(interventions.list(principal, sessionId));
  }

  /** 사용자 반응은 AI 판정·타이머·세션 상태와 분리해 기록한다. */
  @PostMapping("/api/v1/interventions/{interventionId}/acknowledgements")
  @Operation(summary = "개입 확인 또는 닫기")
  public ApiResponse<Acknowledgement> acknowledge(
      @AuthenticationPrincipal FocusOnPrincipal principal, @PathVariable String interventionId,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
      @Valid @RequestBody Acknowledge request) {
    return ApiResponse.of(interventions.acknowledge(principal, deviceId, interventionId, request));
  }

  /** OS 제출/포키 표시 결과를 사용자 반응과 분리한다. */
  @PostMapping("/api/v1/interventions/{interventionId}/deliveries")
  @Operation(summary = "개입 전송 결과 기록")
  public ApiResponse<DeliveryReceipt> delivery(@AuthenticationPrincipal FocusOnPrincipal principal,
      @PathVariable String interventionId,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
      @Valid @RequestBody Delivery request) {
    return ApiResponse.of(deliveries.receive(principal, deviceId, interventionId, request));
  }

}
