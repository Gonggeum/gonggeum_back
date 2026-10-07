package com.gonggeumi.auth;

import com.gonggeumi.common.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class PublicAuthRateLimiter {
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end
            return {n, redis.call('TTL', KEYS[1])}
            """, List.class);
    private final StringRedisTemplate redis;
    private final int limit;

    public PublicAuthRateLimiter(StringRedisTemplate redis, @Value("${app.auth.public-requests-per-minute:60}") int limit) {
        if (limit < 1) throw new IllegalArgumentException("Rate limit must be positive");
        this.redis = redis;
        this.limit = limit;
    }

    public void check(String operation, String remoteAddress) {
        List<?> result;
        try {
            // Forwarded/X-Forwarded-For is deliberately not trusted without a configured proxy boundary.
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(remoteAddress.getBytes(StandardCharsets.UTF_8)));
            result = redis.execute(SCRIPT, List.of("gonggeumi:public-auth:" + operation + ":" + fingerprint));
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new ApiException(503, "DEPENDENCY_UNAVAILABLE", "요청 제한 저장소에 연결할 수 없습니다.", Map.of(), 5);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        if (result == null || result.size() != 2) {
            throw new ApiException(503, "DEPENDENCY_UNAVAILABLE", "요청 제한을 확인할 수 없습니다.", Map.of(), 5);
        }
        if (((Number) result.getFirst()).longValue() > limit) {
            int retry = Math.max(1, ((Number) result.get(1)).intValue());
            throw new ApiException(429, "RATE_LIMITED", "잠시 후 다시 요청해 주세요.", Map.of(), retry);
        }
    }
}
