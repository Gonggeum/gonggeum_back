package com.gonggeumi.auth;

import com.gonggeumi.common.domain.UnsignedId;
import com.gonggeumi.common.web.ApiException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class TermCursor {
    private final byte[] key;
    private final Clock clock;

    public TermCursor(@Value("${app.cursor.signing-key:}") String configuredKey, Clock clock, Environment environment) {
        this.clock = clock;
        if (configuredKey.isBlank()) {
            if (!environment.acceptsProfiles(Profiles.of("local", "test", "mysql-it"))) {
                throw new IllegalStateException("CURSOR_SIGNING_KEY is required outside local/test profiles");
            }
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            key = Base64.getDecoder().decode(configuredKey);
            if (key.length < 32) throw new IllegalStateException("Cursor signing key must contain at least 32 bytes");
        }
    }

    public String issue(String lastId, int limit) {
        UnsignedId.parse(lastId);
        String payload = "terms:v1:" + lastId + ":" + limit + ":" + (clock.instant().getEpochSecond() + 1800);
        return encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + encode(sign(payload));
    }

    public BigInteger read(String token, int limit) {
        if (token == null) return BigInteger.ZERO;
        try {
            if (token.length() > 512) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(payload), Base64.getUrlDecoder().decode(parts[1]))) throw new IllegalArgumentException();
            String[] fields = payload.split(":", -1);
            if (fields.length != 5 || !fields[0].equals("terms") || !fields[1].equals("v1")
                    || Integer.parseInt(fields[3]) != limit || Long.parseLong(fields[4]) <= clock.instant().getEpochSecond()) {
                throw new IllegalArgumentException();
            }
            return UnsignedId.parse(fields[2]).value();
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalid("cursor", "유효하지 않거나 만료된 커서입니다. 처음부터 조회해 주세요.");
        }
    }

    private String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }
}
