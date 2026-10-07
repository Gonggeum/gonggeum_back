package com.gonggeumi.common.web;

import java.util.List;
import java.util.Map;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, Object> details;
    private final Integer retryAfter;

    public ApiException(int status, String code, String message, Map<String, Object> details, Integer retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
        this.retryAfter = retryAfter;
    }

    public static ApiException invalid(String path, String reason) {
        return new ApiException(400, "VALIDATION_ERROR", "입력값을 확인해 주세요.",
                Map.of("fields", List.of(Map.of("path", path, "reason", reason))), null);
    }

    public int status() { return status; }
    public String code() { return code; }
    public Map<String, Object> details() { return details; }
    public Integer retryAfter() { return retryAfter; }
}
