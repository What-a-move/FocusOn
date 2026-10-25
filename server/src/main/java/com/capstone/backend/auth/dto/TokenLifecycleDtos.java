package com.capstone.backend.auth.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 로그인 유지·폐기 계약을 Google 교환 DTO와 분리한다. */
public final class TokenLifecycleDtos {
  private TokenLifecycleDtos() {}

  /** 누락·빈 token은 서비스에서 401로 분류하며 과도한 입력만 400으로 거절한다. */
  public record RefreshRequest(@Size(max = 4096) String refreshToken) {
    @Override
    public String toString() {
      return "RefreshRequest[token=<redacted>]";
    }
  }

  /** 새 Refresh Token 원문은 응답에만 담고 객체 문자열에는 노출하지 않는다. */
  public record TokenPair(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {
    @Override
    public String toString() {
      return "TokenPair[tokens=<redacted>]";
    }
  }
}
