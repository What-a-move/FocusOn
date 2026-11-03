package com.capstone.backend.auth.dto;

import com.capstone.backend.auth.dto.AuthDtos.DeviceInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 연결 코드·설치 ID·token은 객체 문자열에서도 숨긴다. */
public final class PairingDtos {
  private PairingDtos() {}

  /** 코드 형식은 일반 validation 오류가 아닌 LINK_001로 서비스에서 분류한다. */
  public record ClaimRequest(String pairingCode,
      @NotBlank @Size(max = 128) String installationId) {
    @Override
    public String toString() {
      return "ClaimRequest[redacted]";
    }
  }

  /** 원문 코드는 발급 응답에서만 제공한다. */
  public record IssuedCode(String pairingCodeId, String codeDisplay, Instant expiresAt,
      int maxAttempts) {
    @Override
    public String toString() {
      return "IssuedCode[code=<redacted>]";
    }
  }

  /** Desktop과 같은 token 필드와 공개 Extension 기기 명칭을 사용한다. */
  public record LinkedExtension(String accessToken, String refreshToken,
      Instant accessTokenExpiresAt, DeviceInfo device) {
    @Override
    public String toString() {
      return "LinkedExtension[tokens=<redacted>]";
    }
  }
}
