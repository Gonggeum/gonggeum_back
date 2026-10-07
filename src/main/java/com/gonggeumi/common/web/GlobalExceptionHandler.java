package com.gonggeumi.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException exception, HttpServletRequest request) {
        var response = ResponseEntity.status(exception.status());
        if (exception.retryAfter() != null) {
            response.header("Retry-After", exception.retryAfter().toString());
        }
        return response.body(new ApiError(new ApiError.ErrorBody(exception.code(), exception.getMessage(),
                exception.details(), exception.status() == 429 || exception.status() == 503),
                RequestIdFilter.requestId(request)));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException exception,
                                                    HttpServletRequest request) {
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> java.util.Map.of("path", error.getField().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT),
                        "reason", error.getDefaultMessage() == null ? "값을 확인해 주세요." : error.getDefaultMessage()))
                .toList();
        return handleApi(new ApiException(400, "VALIDATION_ERROR", "입력값을 확인해 주세요.",
                java.util.Map.of("fields", fields), null), request);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessResourceFailureException.class)
    public ResponseEntity<ApiError> handleStorage(HttpServletRequest request) {
        return handleApi(new ApiException(503, "DEPENDENCY_UNAVAILABLE", "저장소에 연결할 수 없습니다.",
                java.util.Map.of(), 5), request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handle(Exception exception, HttpServletRequest request) {
        if (exception instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException mismatch) {
            return handleApi(ApiException.invalid(mismatch.getName(), "요청 값의 자료형을 확인해 주세요."), request);
        }
        int status = exception instanceof ErrorResponse error ? error.getStatusCode().value() : 500;
        if (exception instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
            return handleApi(ApiException.invalid("body", "JSON 형식, 필드 이름과 자료형을 확인해 주세요."), request);
        }
        String code = switch (status) {
            case 400 -> "VALIDATION_ERROR";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "INTERNAL_ERROR";
        };
        String message = status >= 500 ? "서버 오류가 발생했습니다." : "요청을 확인해 주세요.";
        if (status >= 500) {
            // 예외 메시지에 SQL·토큰·개인정보가 포함될 수 있어 원문을 기본 로그에 기록하지 않는다.
            log.error("Unhandled request error: request_id={}, type={}",
                    RequestIdFilter.requestId(request), exception.getClass().getName());
        }
        return ResponseEntity.status(status).body(ApiError.of(code, message, RequestIdFilter.requestId(request)));
    }
}
