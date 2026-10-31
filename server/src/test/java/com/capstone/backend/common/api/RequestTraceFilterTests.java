package com.capstone.backend.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** trace 헤더, 개인정보 마스킹과 요청 종료 후 MDC 상태를 검증한다. */
class RequestTraceFilterTests {
  private final RequestIdFilter filter = new RequestIdFilter();
  private final Logger logger = (Logger) LoggerFactory.getLogger(SafeApiLog.class);
  private final ListAppender<ILoggingEvent> events = new ListAppender<>();

  @BeforeEach
  void setUp() {
    events.start();
    logger.addAppender(events);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(events);
    events.stop();
    MDC.clear();
  }

  @Test
  void echoesValidTraceAndKeepsRequestIdIndependent() throws Exception {
    var request = request();
    request.addHeader("X-Trace-Id", "abc-12345");
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> {
      assertEquals("abc-12345", MDC.get("traceId"));
      assertTrue(MDC.get("requestId").startsWith("req_"));
      assertNotEquals(MDC.get("requestId"), MDC.get("traceId"));
      ((MockHttpServletResponse) res).setStatus(204);
    });
    assertEquals("abc-12345", response.getHeader("X-Trace-Id"));
    assertNull(MDC.get("requestId"));
    assertNull(MDC.get("traceId"));
    assertEquals(2, events.list.size());
    assertEquals("abc-12345", events.list.getFirst().getMDCPropertyMap().get("traceId"));
    assertTrue(events.list.getLast().getKeyValuePairs().stream()
        .anyMatch(item -> item.key.equals("status") && item.value.equals(204)));
  }

  @Test
  void invalidMissingAndDuplicateHeadersGetNewIds() throws Exception {
    final var missing = request();
    var invalid = request();
    invalid.addHeader("X-Trace-Id", "bad!secret");
    var duplicate = request();
    duplicate.addHeader("X-Trace-Id", "valid-1234");
    duplicate.addHeader("X-Trace-Id", "valid-5678");
    for (var req : List.of(missing, invalid, duplicate)) {
      var response = new MockHttpServletResponse();
      filter.doFilter(req, response, (a, b) -> {});
      assertTrue(response.getHeader("X-Trace-Id")
          .matches("[A-Za-z0-9-]{8,64}"));
      assertNotEquals("valid-1234", response.getHeader("X-Trace-Id"));
      assertNotEquals("bad!secret", response.getHeader("X-Trace-Id"));
    }
  }

  @Test
  void masksPathAndRestoresMdcWhenDownstreamThrows() throws Exception {
    var req = request();
    req.setServletPath("/api/v1/users/private-email@example.com/profile");
    req.setQueryString("secret=do-not-log");
    MDC.put("traceId", "previous-trace");
    MDC.put("requestId", "previous-request");
    assertThrows(IOException.class, () -> filter.doFilter(req, new MockHttpServletResponse(),
        (a, b) -> {
          throw new IOException("private content");
        }));
    assertEquals("previous-trace", MDC.get("traceId"));
    assertEquals("previous-request", MDC.get("requestId"));
    String entries = events.list.toString() + events.list.stream()
        .map(event -> String.valueOf(event.getKeyValuePairs())).toList();
    assertFalse(entries.contains("private-email"));
    assertFalse(entries.contains("do-not-log"));
    assertFalse(entries.contains("private content"));
    assertTrue(entries.contains("{redacted}"));
    assertTrue(events.list.getLast().getKeyValuePairs().stream()
        .anyMatch(item -> item.key.equals("status") && item.value.equals(500)));
  }

  @Test
  void concurrentRequestsKeepTheirOwnTrace() throws Exception {
    logger.detachAppender(events);
    try (var executor = Executors.newFixedThreadPool(4)) {
      var tasks = java.util.stream.IntStream.range(0, 20).mapToObj(index -> executor.submit(() -> {
        String trace = "request-" + index + "-trace";
        var req = request();
        req.addHeader("X-Trace-Id", trace);
        var response = new MockHttpServletResponse();
        filter.doFilter(req, response, (a, b) -> assertEquals(trace, MDC.get("traceId")));
        assertEquals(trace, response.getHeader("X-Trace-Id"));
        assertNull(MDC.get("traceId"));
        return true;
      })).toList();
      for (var task : tasks) {
        assertTrue(task.get());
      }
    }
  }

  private static MockHttpServletRequest request() {
    var request = new MockHttpServletRequest("GET", "/api/v1/users/me/profile");
    request.setServletPath("/api/v1/users/me/profile");
    return request;
  }
}
