package com.capstone.backend.common.privacy;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 저장·응답 전에 사용자나 AI가 만든 글에 민감값(URL·이메일·전화번호·토큰·인증번호 등)이 섞였는지
 * 본다. 학습 정리 저장 검사와 세션 회고 메모(#318)가 같은 기준을 쓴다. 원문은 로그에 남기지 않는다.
 */
public final class SensitiveText {
  private static final List<Pattern> PATTERNS = List.of(
      Pattern.compile("(?i)\\b(?:https?|ftp)://"),
      Pattern.compile("(?i)\\bwww\\.[a-z0-9-]+\\."),
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
      Pattern.compile("\\b01[016789][-. ]?\\d{3,4}[-. ]?\\d{4}\\b"),
      Pattern.compile("\\b\\d{2,3}-\\d{3,4}-\\d{4}\\b"),
      Pattern.compile("(?i)\\b(?:sk|pk|rk)[-_][A-Za-z0-9_-]{16,}"),
      // 키워드 뒤에 실제 값처럼 보이는 문자열이 붙을 때만 본다("token: 인증에 쓰는 값"은 통과).
      Pattern.compile("(?i)\\b(?:api[_-]?key|token|secret|password|passwd)\\s*[:=]\\s*[\"']?"
          + "[A-Za-z0-9_\\-./+=]{6,}"),
      Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"),
      // 긴 클래스 이름은 통과시키고, 숫자와 문자가 섞인 32자 이상 값만 비밀값으로 본다.
      Pattern.compile("\\b(?=[A-Za-z0-9_-]*\\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{32,}\\b"),
      // 여섯 자리 숫자는 OTP·인증번호 문맥에서만 본다("100000건"은 통과).
      Pattern.compile("(?i)(?:otp|인증\\s*(?:번호|코드)|verification\\s*code)\\D{0,12}"
          + "(?<!\\d)\\d{6}(?!\\d)"));

  private SensitiveText() {}

  /** 민감값으로 보이는 부분이 하나라도 있으면 true다. null은 false다. */
  public static boolean contains(String value) {
    return value != null && PATTERNS.stream().anyMatch(pattern -> pattern.matcher(value).find());
  }
}
