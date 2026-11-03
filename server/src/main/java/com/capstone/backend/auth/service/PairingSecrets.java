package com.capstone.backend.auth.service;

import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 작은 코드 공간의 DB 오프라인 추측을 막는 별도 keyed hash다. */
@Component
public class PairingSecrets {
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private final SecureRandom random = new SecureRandom();
  private final byte[] key;

  /** 배포 간 유지할 32바이트 이상 Base64 키를 강제한다. */
  public PairingSecrets(@Value("${focuson.pairing.secret-base64:}") String secret) {
    try {
      key = Base64.getDecoder().decode(secret);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Pairing key must be valid Base64");
    }
    if (key.length < 32) {
      throw new IllegalArgumentException("Pairing key must contain at least 32 bytes");
    }
  }

  /** 6자리 코드를 생성하고 원문은 응답 전용으로만 전달한다. */
  public String generate() {
    var value = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return value.toString();
  }

  /** 하이픈 표시형과 6자리 원문을 허용하고 그 밖의 구분자는 허용하지 않는다. */
  public String normalize(String raw) {
    if (raw == null || raw.length() > 32) {
      throw new ApiException(ApiErrorCode.LINK_001_INVALID_FORMAT);
    }
    String code = raw.trim().toUpperCase(Locale.ROOT);
    if (!code.matches("[A-Z0-9]{6}|[A-Z0-9]{3}-[A-Z0-9]{3}")) {
      throw new ApiException(ApiErrorCode.LINK_001_INVALID_FORMAT);
    }
    return code.replace("-", "");
  }

  /** 동일한 별도 키로만 조회할 수 있는 hex digest를 반환한다. */
  public String hash(String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA256 unavailable");
    }
  }
}
