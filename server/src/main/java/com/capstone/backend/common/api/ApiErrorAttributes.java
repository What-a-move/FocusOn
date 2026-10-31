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

/** 미구현 API의 내부 오류 전달을 공통 오류 형식으로 변환한다. */
@Component
public class ApiErrorAttributes extends DefaultErrorAttributes {
  @Override
  public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
    Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE,
        RequestAttributes.SCOPE_REQUEST);
    Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI,
        RequestAttributes.SCOPE_REQUEST);
    if (Integer.valueOf(404).equals(status) && uri instanceof String path
        && (path.equals("/api/v1") || path.startsWith("/api/v1/"))
        && request instanceof ServletWebRequest servletRequest) {
      var body = ApiErrorResponse.of(ApiErrorCode.NOT_FOUND, null,
          RequestIdFilter.from(servletRequest.getRequest()), Map.of());
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("code", body.code());
      result.put("message", body.message());
      result.put("retryable", body.retryable());
      result.put("retryAfterSeconds", body.retryAfterSeconds());
      result.put("requestId", body.requestId());
      result.put("details", body.details());
      return result;
    }
    return super.getErrorAttributes(request, options);
  }
}
