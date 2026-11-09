package com.capstone.backend.intervention.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiResponse;
import com.capstone.backend.intervention.dto.ObservationContextDtos.Item;
import com.capstone.backend.intervention.dto.ObservationContextDtos.Put;
import com.capstone.backend.intervention.service.ObservationContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 전경 담당을 명시적으로 지정하고 연결 Extension이 조회한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions/{sessionId}/observation-context")
@Tag(name = "개입")
@SecurityRequirement(name = "focusOnBearer")
public class ObservationContextController {
  private final ObservationContextService contexts;

  /** PUT 재시도는 새 관측이나 lease 연장을 만들지 않는다. */
  @PutMapping
  @Operation(summary = "세션 전경 관측 담당 지정")
  public ApiResponse<Item> put(@AuthenticationPrincipal FocusOnPrincipal principal,
      @PathVariable String sessionId,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
      @Valid @RequestBody Put request) {
    return ApiResponse.of(contexts.put(principal, deviceId, sessionId, request));
  }

  /** 유효 context 없음은 본문 없는204이며 조회로 keepalive하지 않는다. */
  @GetMapping
  @Operation(summary = "세션 전경 관측 담당 조회")
  public ResponseEntity<ApiResponse<Item>> get(@AuthenticationPrincipal FocusOnPrincipal principal,
      @PathVariable String sessionId,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
    return contexts.get(principal, deviceId, sessionId)
        .map(value -> ResponseEntity.ok(ApiResponse.of(value)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }
}
