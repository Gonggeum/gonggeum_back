package com.gonggeumi.auth;

import com.gonggeumi.common.domain.UnsignedId;
import com.gonggeumi.common.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class TermCursorTest {
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final TermCursor cursor = new TermCursor(KEY, Clock.fixed(NOW, ZoneOffset.UTC), new MockEnvironment());

    @Test void unsignedIdsRetainValuesAboveJavaLongMax() {
        String maximum = "18446744073709551615";
        assertThat(cursor.read(cursor.issue(maximum, 20), 20).toString()).isEqualTo(maximum);
        for (String invalid : new String[]{"0", "-1", "01", "18446744073709551616", "1.0", " 1"}) {
            assertThatThrownBy(() -> UnsignedId.parse(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsTamperedAndMalformedTokens() {
        String token = cursor.issue("12", 20);
        String[] parts = token.split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString("terms:v1:13:20:9999999999".getBytes()) + "." + parts[1];
        for (String invalid : new String[]{forged, "", "...", "x".repeat(513)}) {
            assertThatThrownBy(() -> cursor.read(invalid, 20)).isInstanceOf(ApiException.class);
        }
    }

    @Test void expiresAfterThirtyMinutesAndIsBoundToPageSize() {
        String token = cursor.issue("12", 20);
        assertThatThrownBy(() -> cursor.read(token, 21)).isInstanceOf(ApiException.class);
        var later = new TermCursor(KEY, Clock.fixed(NOW.plusSeconds(1800), ZoneOffset.UTC), new MockEnvironment());
        assertThatThrownBy(() -> later.read(token, 20)).isInstanceOf(ApiException.class);
    }

    @Test void productionRequiresSufficientPersistentKey() {
        assertThatThrownBy(() -> new TermCursor("", Clock.systemUTC(), new MockEnvironment())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TermCursor("YWJj", Clock.systemUTC(), new MockEnvironment())).isInstanceOf(IllegalStateException.class);
    }
}
