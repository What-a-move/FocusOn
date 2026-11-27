package com.capstone.backend.session.controller;

import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorResponse;
import com.capstone.backend.session.dto.RetrospectiveDtos.RetrospectiveRequest;
import com.capstone.backend.session.dto.RetrospectiveDtos.RetrospectiveResponse;
import com.capstone.backend.session.service.SessionRetrospectiveService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 세션 끝 한 줄 회고 API다(#318). */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "학습 세션")
@SecurityRequirement(name = "focusOnBearer")
public class SessionRetrospectiveController {
  private final SessionRetrospectiveService retrospectives;

  /** 끝난 세션의 회고를 저장하거나 덮어쓴다. */
  @PutMapping("/{sessionId}/retrospective")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "세션 회고 저장",
      description = "세션당 하나이며 다시 보내면 덮어씁니다. reasonCodes·note는 BAD일 때만 받습니다. "
          + "note는 100자 이내이며 민감정보가 있으면 거절합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "저장 성공"),
      @ApiResponse(responseCode = "400", description = "입력 오류·민감정보",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "세션 없음 또는 소유권 없음",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "409", description = "끝나지 않은 세션",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<RetrospectiveResponse> save(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
      @PathVariable String sessionId, @Valid @RequestBody RetrospectiveRequest request) {
    return com.capstone.backend.common.api.ApiResponse.of(
        retrospectives.save(principal, deviceId, sessionId, request));
  }
}
