package com.gonggeumi.common.web;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    public static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(ATTRIBUTE);
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader("X-Request-ID", requestId);
        response.setHeader("Cache-Control", "private, no-store");
        MDC.put("request_id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("request_id");
        }
    }
}
