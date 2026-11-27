package com.capstone.backend.report.service;

import com.capstone.backend.common.privacy.SensitiveText;
import com.capstone.backend.report.dto.LearningSummaryResponse.KeyConcept;
import java.util.regex.Pattern;

/**
 * 저장 직전 정리 후보를 검사한다. 형식·길이가 맞지 않거나 민감값이 의심되면 결과 전체를
 * 버린다(부분 마스킹하지 않는다). DB의 READY 제약과 같은 기준을 먼저 확인한다.
 */
final class LearningSummaryOutputGuard {
  static final int MAX_TOTAL = 1200;
  static final int MAX_CONCEPTS = 5;
  /** 모델 식별자는 짧은 이름·버전 문자만 허용한다(예: summary-v1, gpt-4o-mini:2026-01). */
  private static final Pattern MODEL_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");

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
    // model_version도 그대로 저장되므로 식별자 형식이 아니거나 민감값이면 결과 전체를 버린다.
    if (draft.modelVersion() != null && (sensitive(draft.modelVersion())
        || !MODEL_VERSION.matcher(draft.modelVersion()).matches())) {
      return "OUTPUT_SENSITIVE";
    }
    return null;
  }

  private static boolean sensitive(String value) {
    return SensitiveText.contains(value);
  }

  /** V20과 같이 공백·BOM만 있는 문자열을 빈 값으로 본다. */
  private static boolean nonBlank(String value) {
    return value != null && value.codePoints().anyMatch(character ->
        !Character.isWhitespace(character) && !Character.isSpaceChar(character)
            && character != 0xfeff);
  }
}
