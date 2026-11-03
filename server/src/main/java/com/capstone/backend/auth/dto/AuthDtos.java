package com.capstone.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 인증 HTTP 계약. 비밀값을 가진 요청·응답은 로그 문자열에서 원문을 숨긴다. */
public final class AuthDtos {
  private AuthDtos() {}

  /** Main이 state를 확인한 뒤 전달하는 Google code 교환 요청이다. */
  public record GoogleExchangeRequest(
      @Schema(description = "PKCE 도구가 Google 인증 뒤 발급한 일회용 code입니다. 예시를 그대로 사용하지 마세요.",
          example = "google-authorization-code-from-pkce-tool")
      @NotBlank @Size(max = 4096) String authorizationCode,
      @Schema(description = "authorizationCode와 같은 PKCE 요청에서 생성한 verifier입니다.",
          example = "VyXyzjLOvDFy.LNHGi2fRnvNzPwHthduqI4vR0v._nqfxn5iz55Tu8IwJPxnUBu13B")
      @NotBlank @Pattern(regexp = "[A-Za-z0-9._~-]{43,128}") String codeVerifier,
      @Schema(description = "PKCE 도구가 생성한 state입니다.", example = "pkce-state-from-tool")
      @NotBlank @Size(max = 512) String state,
      @Schema(description = "PKCE 도구가 생성한 로컬 callback 주소입니다.",
          example = "http://127.0.0.1:49152/")
      @NotBlank @Size(max = 256) String redirectUri,
      @NotNull @Valid DesktopDevice device) {
    @Override
    public String toString() {
      return "GoogleExchangeRequest[redacted]";
    }
  }

  /** 원본 installation ID는 DB에 저장하지 않고 해시로 변환한다. */
  public record DesktopDevice(
      @Schema(description = "QA 전용 Desktop 설치 식별자입니다. 실제 Desktop 식별자는 사용하지 마세요.",
          example = "swagger-local-qa")
      @NotBlank @Size(max = 128) String installationId,
      @Schema(example = "Swagger QA Desktop") @NotBlank @Size(max = 80) String deviceName,
      @Schema(allowableValues = "MACOS", example = "MACOS")
      @NotBlank @Pattern(regexp = "MACOS") String platform) {
    @Override
    public String toString() {
      return "DesktopDevice[redacted]";
    }
  }

  /** API의 DB 식별자는 문자열로 직렬화한다. */
  public record UserInfo(String userId, String nickname) {}

  /** 공개 Extension 명칭과 DB 내부 enum은 응답 계층에서 변환한다. */
  public record DeviceInfo(String deviceId, String deviceType) {}

  /** 인증된 현재 계정·기기 요약이다. */
  public record Identity(UserInfo user, DeviceInfo device, String onboardingStatus) {}

  /** 최초 발급한 토큰. Refresh Token 원문은 이 응답 외에는 보관하지 않는다. */
  public record LoginResponse(
      String accessToken, String refreshToken, Instant accessTokenExpiresAt,
      UserInfo user, DeviceInfo device, String onboardingStatus) {
    @Override
    public String toString() {
      return "LoginResponse[tokens=<redacted>]";
    }
  }
}
