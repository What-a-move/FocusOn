package com.capstone.backend.session.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorResponse;
import com.capstone.backend.session.dto.LocalEventDtos.Request;
import com.capstone.backend.session.dto.LocalEventDtos.Response;
import com.capstone.backend.session.service.LocalEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/** 권한 유실을 기록하고 실행 세션을 안전하게 멈춘다. */
@RestController
@RequiredArgsConstructor
@Tag(name = "학습 세션")
@SecurityRequirement(name = "focusOnBearer")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "입력 또는 필수 헤더 오류",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "인증·기기·연결 오류",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "404", description = "데이터 없음 또는 세션 소유권 없음",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "409", description = "세션 만료·최종 상태 또는 이벤트 저장 한도",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class LocalEventController {
  private final LocalEventService events;

  /** 최초 발생 시각을 유지한 재전송을 받아 중복 상태 전이를 막는다. */
  @PostMapping("/api/v1/sessions/{sessionId}/local-events")
  @ResponseStatus(HttpStatus.OK)
  @ApiResponse(responseCode = "200", description = "이벤트 처리 또는 중복 확인")
  @Operation(summary = "세션 로컬 이벤트 수신",
      description = "Authorization·X-Device-Id 필수. 동일 코드·발생 시각의 재전송은 다시 정지하지 않습니다. "
          + "클라이언트는 재시도 시 최초 occurredAt을 유지해야 합니다. 세션당256건 이후 새 이벤트는 "
          + "정지를 반영한 뒤 재시도 불가409로 응답하며, 기존 재전송은 허용합니다.")
  public com.capstone.backend.common.api.ApiResponse<Response> receive(
      @AuthenticationPrincipal FocusOnPrincipal principal, @PathVariable String sessionId,
      @Parameter(required = true, description = "토큰의 현재 기기 ID")
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
      @Valid @RequestBody Request request) {
    return com.capstone.backend.common.api.ApiResponse.of(
        events.receive(principal, deviceId, sessionId, request));
  }
}
