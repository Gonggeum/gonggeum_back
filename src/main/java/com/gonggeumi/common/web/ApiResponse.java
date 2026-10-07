package com.gonggeumi.common.web;

import jakarta.servlet.http.HttpServletRequest;

public record ApiResponse<T>(T data, Meta meta) {
    public record Meta(String requestId, boolean replayed) {}
    public static <T> ApiResponse<T> of(T data, HttpServletRequest request) {
        return new ApiResponse<>(data, new Meta(RequestIdFilter.requestId(request), false));
    }
}
