package com.gonggeumi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonggeumi.common.web.ApiError;
import com.gonggeumi.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            // A02는 공개 읽기 전용 조회이며 쿠키 인증/상태 변경을 하지 않는다.
            .csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/api/v1/auth/availability", "POST")))
            // JWT + sid 검증 구현 전까지 업무 경로는 전부 차단한다. CSRF 보호도 유지한다.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/terms").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/availability").permitAll()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    writeError(mapper, request, response, 401, "UNAUTHENTICATED", "인증이 필요합니다."))
                .accessDeniedHandler((request, response, exception) ->
                    writeError(mapper, request, response, 403, "FORBIDDEN", "요청 권한을 확인해 주세요.")));
        return http.build();
    }
    private void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                            int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiError.of(code, message, RequestIdFilter.requestId(request)));
    }
}
