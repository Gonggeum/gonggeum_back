package com.gonggeumi.auth;

import com.gonggeumi.common.web.ApiException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AvailabilityService {
    private final JdbcClient jdbc;
    private final Validator validator;

    public AvailabilityService(JdbcClient jdbc, Validator validator) {
        this.jdbc = jdbc;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public Availability check(String field, String rawValue) {
        String value = rawValue.strip();
        String sql;
        if ("login_id".equals(field)) {
            if (!value.matches("[a-z0-9]{4,20}")) {
                throw ApiException.invalid("value", "아이디는 영문 소문자/숫자 4~20자로 입력해 주세요.");
            }
            sql = "SELECT COUNT(*) FROM users WHERE login_id = :value";
        } else if ("email".equals(field)) {
            value = value.toLowerCase(Locale.ROOT);
            if (value.isBlank() || value.length() > 254 || !validator.validate(new EmailValue(value)).isEmpty()) {
                throw ApiException.invalid("value", "올바른 이메일을 입력해 주세요.");
            }
            sql = "SELECT COUNT(*) FROM users WHERE email = :value";
        } else {
            throw ApiException.invalid("field", "login_id 또는 email만 허용합니다.");
        }
        return new Availability(jdbc.sql(sql).param("value", value).query(Long.class).single() == 0, value);
    }

    public record Availability(boolean isAvailable, String normalizedValue) {}
    private record EmailValue(@Email String value) {}
}
