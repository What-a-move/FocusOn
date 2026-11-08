package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import java.net.IDN;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/** AI 전송 전의 추가 방어다. 클라이언트 Privacy Guard를 대체하지 않는다. */
final class AnalysisPrivacy {
  // PostgreSQL 17 timestamp.h: Julian day zero inclusive, year 294277 exclusive.
  private static final Instant FIRST_TIMESTAMP = Instant.parse("-4713-11-24T00:00:00Z");
  private static final Instant END_TIMESTAMP = Instant.parse("+294277-01-01T00:00:00Z");
  private static final Pattern SENSITIVE = Pattern.compile(
      "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{8,}|"
      + "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|"
      + "\\b(?:sk-[A-Za-z0-9_-]{16,}|ghp_[A-Za-z0-9]{20,}|"
      + "github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})\\b|"
      + "[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|"
      + "(?<!\\d)01[016789][ -]?\\d{3,4}[ -]?\\d{4}(?!\\d)");

  private AnalysisPrivacy() {}

  /** 출처와 보호 상태로 허용할 입력을 제한해 제외 검사를 우회한 본문 전송을 막는다. */
  static void validate(Request request) {
    // JDBC rounds nanos to micros; prevent rounding the final half microsecond out of range.
    if (request.observedAt().isBefore(FIRST_TIMESTAMP)
        || !request.observedAt().isBefore(END_TIMESTAMP.minusNanos(500))) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    boolean metadataSource = request.contentSource() == ContentSource.APP_META
        || request.contentSource() == ContentSource.TAB_META;
    if (request.guardStatus() != null) {
      if (request.metadata() != null || request.contentPayload() != null) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
    } else if (metadataSource && request.contentPayload() != null) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    String host = host(request);
    // 본문 출처를 모르면 서버 제외 목록을 확인할 수 없으므로 AI 호출 전에 거절한다.
    boolean webContent = request.contentSource() == ContentSource.TAB_META
        || (request.contentSource() == ContentSource.CHROME_DOM
        && (request.metadata() != null || request.contentPayload() != null));
    if (request.guardStatus() == null && webContent && host.isEmpty()) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
  }

  /** 경로를 잘라 수용하지 않고 host만 정규화해 제외 목록과 비교할 수 있게 한다. */
  static String host(Request request) {
    String input = request.metadata() == null ? null : request.metadata().urlHost();
    if (input == null || input.isEmpty()) {
      return "";
    }
    try {
      // 전체 URL/port/path/query는 정규화해 저장하는 대신 입력 오류로 거절한다.
      String host = input.endsWith(".") ? input.substring(0, input.length() - 1) : input;
      host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
      if (host.length() > 253 || !host.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"
          + "(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*")) {
        throw new IllegalArgumentException();
      }
      return host;
    } catch (IllegalArgumentException exception) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
  }

  /** 원문을 전송하기 전에 목표·메타데이터·본문 전체에 같은 추가 보호 검사를 적용한다. */
  static boolean containsSensitive(Request request, String goal) {
    var metadata = request.metadata();
    return sensitive(goal) || (request.contentPayload() != null
        && sensitive(request.contentPayload().text())) || (metadata != null
        && (sensitive(metadata.appName()) || sensitive(metadata.windowTitle())
        || sensitive(metadata.pageTitle()) || sensitive(metadata.urlHost())));
  }

  /** 눈에 보이지 않는 문자로 알려진 민감 패턴 검사를 우회하지 못하게 정규화한다. */
  private static boolean sensitive(String value) {
    if (value == null) {
      return false;
    }
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replaceAll("\\p{Cf}", "");
    return SENSITIVE.matcher(normalized).find();
  }
}
