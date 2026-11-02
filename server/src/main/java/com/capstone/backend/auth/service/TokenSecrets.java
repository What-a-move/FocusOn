package com.capstone.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 설치 식별자·Refresh Token을 비가역 해시로 보관하기 위한 내부 유틸리티다. */
public final class TokenSecrets {
  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenSecrets() {}

  /** 충분한 엔트로피의 불투명 Refresh Token을 생성한다. */
  public static String newRefreshToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 로그·DB에 원문 대신 저장할 SHA-256 hex다. */
  public static String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }
}
