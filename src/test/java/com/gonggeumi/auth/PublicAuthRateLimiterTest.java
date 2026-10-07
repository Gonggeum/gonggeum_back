package com.gonggeumi.auth;

import com.gonggeumi.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicAuthRateLimiterTest {
    @Test void unavailableRedisFailsClosedWithoutLeakingConnectionDetails() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
            .thenThrow(new RedisConnectionFailureException("private redis host"));
        var limiter = new PublicAuthRateLimiter(redis, 60);
        assertThatThrownBy(() -> limiter.check("availability", "127.0.0.1"))
            .isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.status()).isEqualTo(503);
                assertThat(e.code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
                assertThat(e.getMessage()).doesNotContain("private redis host");
            });
    }
}
