package com.capstone.backend.report.service;

import com.capstone.backend.report.dto.LearningSummaryResponse.KeyConcept;
import com.capstone.backend.report.persistence.LearningSummaryEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * AI가 만든 정리 후보를 저장 직전에 검사한다. 하나라도 어긋나면 결과 전체를 버리며, 민감값을
 * 부분 마스킹해 저장하지 않는다(AI PLAN 생성·출력 계약 6).
 */
final class LearningSummaryOutputValidator {
  static final int MAX_CONCEPTS = 5;
  static final int MAX_TOTAL_LENGTH = 1200;
  private static final Set<String> TOP_LEVEL_FIELDS =
      Set.of("overview", "keyConcepts", "nextStartPrompt", "modelVersion", "promptVersion");
  private static final Set<String> CONCEPT_FIELDS =
      Set.of("name", "briefExplanation", "sessionEvidence");
  /**
   * URL·이메일·전화번호·토큰/API Key·OTP로 보이는 값이다. 학습 내용에서 오탐이 나도 결과를
   * 버리는 쪽이 원문 유출보다 안전하므로 보수적으로 잡는다.
   */
  private static final List<Pattern> SENSITIVE = List.of(
      Pattern.compile("(?i)\\b(?:https?|ftp|file)://"),
      Pattern.compile("(?i)\\bwww\\.[a-z0-9-]+"),
      Pattern.compile("(?i)\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\."
          + "(?:com|net|org|io|kr|dev|app|ai|co|me|xyz|edu|gov)(?:/|\\b)"),
      Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"),
      Pattern.compile("(?<!\\d)(?:\\+?82[- ]?)?0?1[016789][- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)"),
      Pattern.compile("(?<!\\d)\\d{2,3}-\\d{3,4}-\\d{4}(?!\\d)"),
      Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),
      Pattern.compile("\\b(?:sk|pk|rk)[-_][A-Za-z0-9_-]{16,}"),
      Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
      Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{20,}"),
      Pattern.compile("\\bAIza[0-9A-Za-z_-]{30,}"),
      Pattern.compile("(?i)\\b(?:bearer|api[_-]?key|secret|password|token)\\s*[:=]"),
      Pattern.compile("\\b(?=[A-Za-z0-9_-]*\\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{32,}\\b"),
      Pattern.compile("(?<![\\d.])\\d{6,8}(?![\\d.])"));

  private LearningSummaryOutputValidator() {}

  /** 계약에 맞는 정리만 반환한다. 빈 값은 FAILED로 처리해야 한다는 뜻이다. */
  static Optional<Result> validate(JsonNode value) {
    if (value == null || !value.isObject() || !onlyFields(value, TOP_LEVEL_FIELDS)) {
      return Optional.empty();
    }
    String overview = text(value.get("overview"));
    String nextStartPrompt = text(value.get("nextStartPrompt"));
    JsonNode concepts = value.get("keyConcepts");
    if (overview == null || nextStartPrompt == null || concepts == null || !concepts.isArray()
        || concepts.isEmpty() || concepts.size() > MAX_CONCEPTS) {
      return Optional.empty();
    }
    long totalLength = length(overview) + length(nextStartPrompt);
    List<KeyConcept> keyConcepts = new ArrayList<>();
    for (JsonNode concept : concepts) {
      if (!concept.isObject() || !onlyFields(concept, CONCEPT_FIELDS)) {
        return Optional.empty();
      }
      String name = text(concept.get("name"));
      String explanation = text(concept.get("briefExplanation"));
      JsonNode evidenceNode = concept.get("sessionEvidence");
      String evidence = evidenceNode == null || evidenceNode.isNull() ? null : text(evidenceNode);
      if (name == null || explanation == null
          || (evidenceNode != null && !evidenceNode.isNull() && evidence == null)) {
        return Optional.empty();
      }
      totalLength += length(name) + length(explanation) + (evidence == null ? 0 : length(evidence));
      keyConcepts.add(new KeyConcept(name, explanation, evidence));
    }
    if (totalLength > MAX_TOTAL_LENGTH || sensitive(overview) || sensitive(nextStartPrompt)
        || keyConcepts.stream().anyMatch(concept -> sensitive(concept.name())
            || sensitive(concept.briefExplanation()) || sensitive(concept.sessionEvidence()))) {
      return Optional.empty();
    }
    return Optional.of(new Result(overview, List.copyOf(keyConcepts), nextStartPrompt));
  }

  /** 민감값으로 보이는 패턴이 하나라도 있으면 true다. */
  static boolean sensitive(String value) {
    return value != null && SENSITIVE.stream().anyMatch(pattern -> pattern.matcher(value).find());
  }

  private static boolean onlyFields(JsonNode node, Set<String> allowed) {
    return node.propertyNames().stream().allMatch(allowed::contains);
  }

  /** 공백만 있는 값은 DB CHECK(V20)와 같은 기준으로 없는 값으로 본다. */
  private static String text(JsonNode node) {
    return node != null && node.isString() && LearningSummaryEntity.nonBlank(node.asString())
        ? node.asString() : null;
  }

  /** PostgreSQL length()와 같게 UTF-16 단위가 아닌 문자(code point) 수로 센다. */
  private static long length(String value) {
    return value.codePointCount(0, value.length());
  }

  /** 저장 가능한 정리 결과다. */
  record Result(String overview, List<KeyConcept> keyConcepts, String nextStartPrompt) {}
}
