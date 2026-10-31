package com.capstone.backend.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Controller에서 발생한 오류를 FocusOn 공통 응답 형식으로 변환한다. */
@RestControllerAdvice(basePackages = "com.capstone.backend")
public class ApiExceptionHandler {
  /** 도메인에서 지정한 오류와 429 대기 시간을 공통 형식으로 반환한다. */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(
      ApiException exception, HttpServletRequest request) {
    String requestId = RequestIdFilter.from(request);
    ApiErrorCode code = exception.code();
    request.setAttribute(RequestIdFilter.ERROR_CODE_ATTRIBUTE, code);
    request.setAttribute(RequestIdFilter.ERROR_STAGE_ATTRIBUTE, SafeApiLog.Stage.REQUEST);
    ApiErrorResponse body = ApiErrorResponse.of(
        code, exception.retryAfterSeconds(), requestId, exception.details());
    ResponseEntity.BodyBuilder response = ResponseEntity.status(code.status());
    if (exception.retryAfterSeconds() != null) {
      response.header(HttpHeaders.RETRY_AFTER, exception.retryAfterSeconds().toString());
    }
    return response.body(body);
  }

  /** DTO·JSON·필수 헤더 등 요청 입력 오류를 400으로 변환한다. */
  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      HandlerMethodValidationException.class,
      HttpMessageNotReadableException.class,
      ServletRequestBindingException.class,
      MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiErrorResponse> handleValidation(
      Exception exception, HttpServletRequest request) {
    // 원문 입력이나 프레임워크 예외 메시지는 details와 로그에 포함하지 않는다.
    request.setAttribute(RequestIdFilter.ERROR_CODE_ATTRIBUTE, ApiErrorCode.VALIDATION_ERROR);
    request.setAttribute(RequestIdFilter.ERROR_STAGE_ATTRIBUTE, SafeApiLog.Stage.VALIDATION);
    if (exception instanceof MethodArgumentNotValidException validation) {
      boolean logged = false;
      for (var fieldError : validation.getBindingResult().getFieldErrors()) {
        String field = fieldError.getField();
        if (field.matches("[A-Za-z][A-Za-z0-9]{0,63}")) {
          Class<?> type = validation.getBindingResult().getFieldType(field);
          SafeApiLog.validationFailure(field, type);
          logged = true;
        }
      }
      if (!logged) {
        SafeApiLog.validationFailure(null, null);
      }
    } else {
      SafeApiLog.validationFailure(null, null);
    }
    return error(ApiErrorCode.VALIDATION_ERROR, RequestIdFilter.from(request));
  }

  /** 예상 밖의 오류에서 내부 원문을 숨기고 안전한 500 응답을 반환한다. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(
      Exception exception, HttpServletRequest request) {
    String requestId = RequestIdFilter.from(request);
    request.setAttribute(RequestIdFilter.ERROR_CODE_ATTRIBUTE, ApiErrorCode.INTERNAL_ERROR);
    request.setAttribute(RequestIdFilter.ERROR_STAGE_ATTRIBUTE,
        exception instanceof DataAccessException ? SafeApiLog.Stage.DATABASE
            : SafeApiLog.Stage.SERVER);
    // 예외 메시지와 stack trace에는 요청 본문·토큰이 포함될 수 있어 기록하지 않는다.
    return error(ApiErrorCode.INTERNAL_ERROR, requestId);
  }

  private static ResponseEntity<ApiErrorResponse> error(ApiErrorCode code, String requestId) {
    return ResponseEntity.status(code.status())
        .body(ApiErrorResponse.of(code, null, requestId, Map.of()));
  }
}
