package com.capstone.backend.auth.dto;

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
      @NotBlank @Size(max = 4096) String authorizationCode,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9._~-]{43,128}") String codeVerifier,
      @NotBlank @Size(max = 512) String state,
      @NotBlank @Size(max = 256) String redirectUri,
      @NotNull @Valid DesktopDevice device) {
    @Override
    public String toString() {
      return "GoogleExchangeRequest[redacted]";
    }
  }

  /** 원본 installation ID는 DB에 저장하지 않고 해시로 변환한다. */
  public record DesktopDevice(
      @NotBlank @Size(max = 128) String installationId,
      @NotBlank @Size(max = 80) String deviceName,
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
