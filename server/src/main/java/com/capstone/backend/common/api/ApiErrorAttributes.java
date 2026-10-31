package com.capstone.backend.common.api;

import jakarta.servlet.RequestDispatcher;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/** API의 내부 오류 전달을 원래 상태를 보존한 공통 오류 형식으로 변환한다. */
@Component
public class ApiErrorAttributes extends DefaultErrorAttributes {
  @Override
  public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
    Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE,
        RequestAttributes.SCOPE_REQUEST);
    Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI,
        RequestAttributes.SCOPE_REQUEST);
    if (status instanceof Integer statusCode && (statusCode == 404 || statusCode == 405)
        && uri instanceof String path
        && (path.equals("/api/v1") || path.startsWith("/api/v1/"))
        && request instanceof ServletWebRequest servletRequest) {
      String code = switch (statusCode) {
        case 404 -> ApiErrorCode.NOT_FOUND.name();
        case 405 -> "METHOD_NOT_ALLOWED";
        default -> "HTTP_ERROR";
      };
      String message = statusCode == 404 ? ApiErrorCode.NOT_FOUND.defaultMessage()
          : "요청 방식이 허용되지 않습니다.";
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("code", code);
      result.put("message", message);
      result.put("retryable", false);
      result.put("retryAfterSeconds", null);
      result.put("requestId", RequestIdFilter.from(servletRequest.getRequest()));
      result.put("details", Map.of());
      return result;
    }
    return super.getErrorAttributes(request, options);
  }
}
