package com.capstone.backend.user.controller;

import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.ConsentResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.ConsentsResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.ExcludedDomainsResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.FocusSettingsResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.MeResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.ReplaceExcludedDomainsRequest;
import com.capstone.backend.user.dto.UserConfigurationDtos.UpdateConsentRequest;
import com.capstone.backend.user.dto.UserConfigurationDtos.UpdateFocusSettingsRequest;
import com.capstone.backend.user.dto.UserConfigurationDtos.UpdateProfileRequest;
import com.capstone.backend.user.persistence.UserConsentEntity.ConsentType;
import com.capstone.backend.user.service.UserConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 현재 인증 사용자의 프로필과 기본 설정을 제공한다. */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "사용자 설정", description = "현재 인증 사용자의 프로필, 동의, 집중 설정 API")
@SecurityRequirement(name = "focusOnBearer")
public class UserConfigurationController {
  private final UserConfigurationService configuration;

  /** 토큰이 가리키는 현재 사용자만 반환한다. */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "현재 사용자 조회",
      description = "Bearer 토큰이 가리키는 사용자 정보만 반환합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200", description = "조회 성공"),
      @ApiResponse(
          responseCode = "401", description = "인증 필요",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<MeResponse> me(
      @AuthenticationPrincipal FocusOnPrincipal principal) {
    return com.capstone.backend.common.api.ApiResponse.of(configuration.me(principal));
  }

  /** 현재 사용자의 닉네임을 저장한다. */
  @PutMapping("/profile")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "프로필 수정", description = "현재 사용자의 닉네임을 변경합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200", description = "저장 성공"),
      @ApiResponse(
          responseCode = "400", description = "닉네임 형식 오류",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401", description = "인증 필요",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "409", description = "닉네임 중복",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<MeResponse> updateProfile(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @Valid @org.springframework.web.bind.annotation.RequestBody UpdateProfileRequest request) {
    return com.capstone.backend.common.api.ApiResponse.of(
        configuration.updateProfile(principal, request));
  }

  /** 현재 사용자 동의 상태를 조회한다. */
  @GetMapping("/consents")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "동의 상태 조회",
      description = "현재 문서 버전과 사용자 동의 상태를 조회합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200", description = "조회 성공"),
      @ApiResponse(
          responseCode = "401", description = "인증 필요",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<ConsentsResponse> consents(
      @AuthenticationPrincipal FocusOnPrincipal principal) {
    return com.capstone.backend.common.api.ApiResponse.of(configuration.consents(principal));
  }

  /** 현재 사용자 동의 한 항목을 저장한다. */
  @PutMapping("/consents/{consentType}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "동의 상태 수정",
      description = "명세에 정의된 동의 문서 한 항목을 저장합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200", description = "저장 성공"),
      @ApiResponse(
          responseCode = "400", description = "동의 종류 또는 버전 오류",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401", description = "인증 필요",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<ConsentResponse> updateConsent(
      @AuthenticationPrincipal FocusOnPrincipal principal, @PathVariable ConsentType consentType,
      @Valid @org.springframework.web.bind.annotation.RequestBody UpdateConsentRequest request) {
    return com.capstone.backend.common.api.ApiResponse.of(
        configuration.updateConsent(principal, consentType, request));
  }

  /** 현재 사용자의 집중 설정을 조회한다. */
  @GetMapping("/focus-settings")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "집중 설정 조회", description = "현재 사용자의 기본 집중 설정을 조회합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200", description = "조회 성공"),
      @ApiResponse(
          responseCode = "401", description = "인증 필요",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<FocusSettingsResponse> focusSettings(
      @AuthenticationPrincipal FocusOnPrincipal principal) {
    return com.capstone.backend.common.api.ApiResponse.of(configuration.focusSettings(principal));
  }

  /** 현재 사용자의 집중 설정 전체를 저장한다. */
  @PutMapping("/focus-settings")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "집중 설정 수정",
      description = "개입 강도와 알림 채널을 포함한 기본 설정을 저장합니다.",
      requestBody = @RequestBody(
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"interventionLevel\":\"NORMAL\",\"notificationChannels\":[\"MOLE_UI\"],"
                  + "\"breakSuggestionEnabled\":true,\"cameraAnalysisEnabled\":false}"))))
  @ApiResponses({
      @ApiResponse(
          responseCode = "200", description = "저장 성공"),
      @ApiResponse(
          responseCode = "400", description = "설정값 오류",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401", description = "인증 필요",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<FocusSettingsResponse> updateFocusSettings(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @Valid @org.springframework.web.bind.annotation.RequestBody
          UpdateFocusSettingsRequest request) {
    return com.capstone.backend.common.api.ApiResponse.of(
        configuration.updateFocusSettings(principal, request));
  }

  /** 현재 사용자와 현재 기기의 분석 제외 host를 조회한다. */
  @GetMapping("/analysis-excluded-domains")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "분석 제외 도메인 조회",
      description = "현재 사용자·기기의 분석 제외 host 목록을 조회합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200", description = "조회 성공"),
      @ApiResponse(
          responseCode = "400", description = "기기 헤더 누락",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401", description = "인증 또는 기기 소유권 오류",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<ExcludedDomainsResponse> excludedDomains(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @Parameter(name = "X-Device-Id", in = ParameterIn.HEADER, required = true,
          description = "현재 인증 기기의 서버 ID")
      @RequestHeader("X-Device-Id") String deviceId) {
    DeviceHeaderValidator.require(deviceId, principal);
    return com.capstone.backend.common.api.ApiResponse.of(configuration.excludedDomains(principal));
  }

  /** 현재 사용자와 현재 기기의 분석 제외 host 전체를 교체한다. */
  @PutMapping("/analysis-excluded-domains")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "분석 제외 도메인 전체 교체",
      description = "정규화된 host 목록을 현재 사용자·기기에 저장합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200", description = "저장 성공"),
      @ApiResponse(
          responseCode = "400", description = "도메인 또는 기기 헤더 오류",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401", description = "인증 또는 기기 소유권 오류",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public com.capstone.backend.common.api.ApiResponse<ExcludedDomainsResponse>
      replaceExcludedDomains(
      @AuthenticationPrincipal FocusOnPrincipal principal,
      @Parameter(name = "X-Device-Id", in = ParameterIn.HEADER, required = true,
          description = "현재 인증 기기의 서버 ID")
      @RequestHeader("X-Device-Id") String deviceId,
      @Valid @org.springframework.web.bind.annotation.RequestBody
          ReplaceExcludedDomainsRequest request) {
    DeviceHeaderValidator.require(deviceId, principal);
    return com.capstone.backend.common.api.ApiResponse.of(
        configuration.replaceExcludedDomains(principal, request));
  }
}
