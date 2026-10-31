package com.capstone.backend.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;
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
  public static final String ERROR_CODE_ATTRIBUTE = RequestIdFilter.class.getName() + ".errorCode";
  public static final String ERROR_STAGE_ATTRIBUTE =
      RequestIdFilter.class.getName() + ".errorStage";
  private static final String MDC_KEY = "requestId";
  private static final String TRACE_HEADER = "X-Trace-Id";
  private static final Pattern TRACE_PATTERN = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return !path.equals("/api/v1") && !path.startsWith("/api/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = newRequestId();
    var traces = Collections.list(request.getHeaders(TRACE_HEADER));
    String traceId = traces.size() == 1 && TRACE_PATTERN.matcher(traces.getFirst()).matches()
        ? traces.getFirst() : UUID.randomUUID().toString();
    String previousRequestId = MDC.get(MDC_KEY);
    String previousTraceId = MDC.get("traceId");
    request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
    MDC.put(MDC_KEY, requestId);
    MDC.put("traceId", traceId);
    response.setHeader(TRACE_HEADER, traceId);
    long start = System.nanoTime();
    String method = request.getMethod();
    String path = request.getServletPath();
    boolean failed = false;
    try {
      SafeApiLog.httpIn(method, path);
      chain.doFilter(request, response);
    } catch (IOException | ServletException | RuntimeException exception) {
      failed = true;
      throw exception;
    } finally {
      int status = failed && response.getStatus() < 400 ? 500 : response.getStatus();
      try {
        Object stage = request.getAttribute(ERROR_STAGE_ATTRIBUTE);
        Object code = request.getAttribute(ERROR_CODE_ATTRIBUTE);
        SafeApiLog.httpOut(method, path, status, (System.nanoTime() - start) / 1_000_000,
            stage instanceof SafeApiLog.Stage known ? known
                : status >= 500 ? SafeApiLog.Stage.SERVER : SafeApiLog.Stage.REQUEST,
            code instanceof ApiErrorCode known ? known : null);
      } finally {
        restoreMdc(MDC_KEY, previousRequestId);
        restoreMdc("traceId", previousTraceId);
      }
    }
  }

  private static void restoreMdc(String key, String previous) {
    if (previous == null) {
      MDC.remove(key);
    } else {
      MDC.put(key, previous);
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
