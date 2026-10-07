package com.gonggeumi.common.web;

import java.util.Map;

public record ApiError(ErrorBody error, String requestId) {
    public record ErrorBody(String code, String message, Map<String, Object> details, boolean retryable) {}
    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(new ErrorBody(code, message, Map.of(), false), requestId);
    }
}
