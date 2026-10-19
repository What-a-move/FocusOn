package com.capstone.backend.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** API 요청마다 서버가 장애 추적용 식별자를 부여한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
  public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
  private static final String MDC_KEY = "requestId";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return !path.equals("/api/v1") && !path.startsWith("/api/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // 클라이언트의 추적 ID를 신뢰하면 다른 요청의 로그를 같은 ID로 위장할 수 있다.
    String requestId = newRequestId();
    request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
    MDC.put(MDC_KEY, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  /** 현재 요청의 서버 발급 추적 ID를 반환한다. */
  public static String from(HttpServletRequest request) {
    Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
    return value instanceof String requestId ? requestId : newRequestId();
  }

  private static String newRequestId() {
    return "req_" + UUID.randomUUID().toString().replace("-", "");
  }
}
