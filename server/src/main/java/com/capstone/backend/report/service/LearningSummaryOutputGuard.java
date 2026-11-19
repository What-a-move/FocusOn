package com.capstone.backend.report.service;

import com.capstone.backend.report.dto.LearningSummaryResponse.KeyConcept;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 저장 직전 정리 후보를 검사한다. 형식·길이가 맞지 않거나 민감값이 의심되면 결과 전체를
 * 버린다(부분 마스킹하지 않는다). DB의 READY 제약과 같은 기준을 먼저 확인한다.
 */
final class LearningSummaryOutputGuard {
  static final int MAX_TOTAL = 1200;
  static final int MAX_CONCEPTS = 5;
  private static final List<Pattern> SENSITIVE = List.of(
      Pattern.compile("(?i)\\b(?:https?|ftp)://"),
      Pattern.compile("(?i)\\bwww\\.[a-z0-9-]+\\."),
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
      Pattern.compile("\\b01[016789][-. ]?\\d{3,4}[-. ]?\\d{4}\\b"),
      Pattern.compile("\\b\\d{2,3}-\\d{3,4}-\\d{4}\\b"),
      Pattern.compile("(?i)\\b(?:sk|pk|rk)[-_][A-Za-z0-9_-]{16,}"),
      Pattern.compile("(?i)\\b(?:api[_-]?key|token|secret|password|otp)\\s*[:=]"),
      Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"),
      Pattern.compile("\\b[A-Za-z0-9_-]{32,}\\b"),
      Pattern.compile("(?<!\\d)\\d{6}(?!\\d)"));

  private LearningSummaryOutputGuard() {}

  /** 통과하면 null, 실패하면 저장할 고정 오류 코드를 반환한다. */
  static String reject(LearningSummaryAiClient.Draft draft) {
    if (!nonBlank(draft.overview()) || !nonBlank(draft.nextStartPrompt())
        || draft.keyConcepts().size() > MAX_CONCEPTS) {
      return "OUTPUT_INVALID";
    }
    int total = draft.overview().length() + draft.nextStartPrompt().length();
    for (KeyConcept concept : draft.keyConcepts()) {
      if (!nonBlank(concept.name()) || !nonBlank(concept.briefExplanation())) {
        return "OUTPUT_INVALID";
      }
      total += concept.name().length() + concept.briefExplanation().length()
          + (concept.sessionEvidence() == null ? 0 : concept.sessionEvidence().length());
    }
    if (total > MAX_TOTAL) {
      return "OUTPUT_TOO_LONG";
    }
    if (sensitive(draft.overview()) || sensitive(draft.nextStartPrompt())
        || draft.keyConcepts().stream().anyMatch(concept -> sensitive(concept.name())
            || sensitive(concept.briefExplanation()) || sensitive(concept.sessionEvidence()))) {
      return "OUTPUT_SENSITIVE";
    }
    return null;
  }

  private static boolean sensitive(String value) {
    return value != null && SENSITIVE.stream().anyMatch(pattern -> pattern.matcher(value).find());
  }

  /** V20과 같이 공백·BOM만 있는 문자열을 빈 값으로 본다. */
  private static boolean nonBlank(String value) {
    return value != null && value.codePoints().anyMatch(character ->
        !Character.isWhitespace(character) && !Character.isSpaceChar(character)
            && character != 0xfeff);
  }
}
