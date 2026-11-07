package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import java.net.IDN;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** AI 전송 전의 추가 방어다. 클라이언트 Privacy Guard를 대체하지 않는다. */
final class AnalysisPrivacy {
  private static final Pattern SENSITIVE = Pattern.compile(
      "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{8,}|"
      + "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|"
      + "\\b(?:sk-[A-Za-z0-9_-]{16,}|ghp_[A-Za-z0-9]{20,}|"
      + "github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})\\b|"
      + "[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|"
      + "(?<!\\d)01[016789][ -]?\\d{3,4}[ -]?\\d{4}(?!\\d)");

  private AnalysisPrivacy() {}

  static void validate(Request request) {
    boolean metadataSource = request.contentSource() == ContentSource.APP_META
        || request.contentSource() == ContentSource.TAB_META;
    if (request.guardStatus() != null) {
      if (request.metadata() != null || request.contentPayload() != null) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
    } else if (metadataSource && request.contentPayload() != null) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    host(request);
  }

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

  static boolean containsSensitive(Request request, String goal) {
    var metadata = request.metadata();
    return sensitive(goal) || (request.contentPayload() != null
        && sensitive(request.contentPayload().text())) || (metadata != null
        && (sensitive(metadata.appName()) || sensitive(metadata.windowTitle())
        || sensitive(metadata.pageTitle()) || sensitive(metadata.urlHost())));
  }

  private static boolean sensitive(String value) {
    if (value == null) {
      return false;
    }
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replaceAll("\\p{Cf}", "");
    return SENSITIVE.matcher(normalized).find();
  }
}
