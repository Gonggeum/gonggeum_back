package com.gonggeumi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql-it")
class AuthFoundationIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired Flyway flyway;
    @Autowired PublicAuthRateLimiter limiter;
    private static final AtomicInteger CLIENT_SEQUENCE = new AtomicInteger();
    private String client;

    @BeforeEach void isolatedDatabaseOnly() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("gonggeumi_test");
        client = "it-" + UUID.randomUUID() + "-" + CLIENT_SEQUENCE.incrementAndGet();
    }

    private MockHttpServletRequestBuilder fromClient(MockHttpServletRequestBuilder request) {
        return request.with(r -> { r.setRemoteAddr(client); return r; });
    }

    private MockHttpServletRequestBuilder availability(String json) {
        return fromClient(post("/api/v1/auth/availability").contentType("application/json").content(json));
    }

    @Test void migrationsValidateAndReapplyWithoutChangingSchema() {
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("READ-COMMITTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()", Integer.class)).isEqualTo(17);
    }

    @Test void emptyTermsAreNotReplacedByFakeLegalText() throws Exception {
        mvc.perform(fromClient(get("/api/v1/terms")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.page.has_next").value(false))
            .andExpect(jsonPath("$.data.page.next_cursor").isEmpty())
            .andExpect(jsonPath("$.data.page.limit").value(20));
    }

    @Test @Transactional void currentTermsUseLatestEffectiveVersionsAndSignedPagination() throws Exception {
        term("SERVICE", "old", -100, null);
        term("SERVICE", "current", -50, null);
        term("PRIVACY", "current", -50, null);
        term("MARKETING", "future", 100, null);
        term("MARKETING", "retired", -100, -10);
        var first = mvc.perform(fromClient(get("/api/v1/terms").param("limit", "1")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].version_name").value("current"))
            .andExpect(jsonPath("$.data.items[0].id").isString())
            .andExpect(jsonPath("$.data.items[0].published_at").value(org.hamcrest.Matchers.endsWith("Z")))
            .andExpect(jsonPath("$.data.page.has_next").value(true)).andReturn();
        String cursor = mapper.readTree(first.getResponse().getContentAsString()).at("/data/page/next_cursor").asText();
        mvc.perform(fromClient(get("/api/v1/terms").param("limit", "1").param("cursor", cursor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].term_type").value("PRIVACY"))
            .andExpect(jsonPath("$.data.page.has_next").value(false));
        mvc.perform(fromClient(get("/api/v1/terms").param("limit", "2").param("cursor", cursor)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.fields[0].path").value("cursor"));
    }

    @Test void invalidPageSizeAndCursorAreRejected() throws Exception {
        for (String limit : List.of("0", "101", "text")) {
            mvc.perform(fromClient(get("/api/v1/terms").param("limit", limit))).andExpect(status().isBadRequest());
        }
        mvc.perform(fromClient(get("/api/v1/terms").param("cursor", "tampered"))).andExpect(status().isBadRequest());
    }

    @Test @Transactional void duplicateLookupNormalizesInputWithoutCreatingOrReservingUsers() throws Exception {
        user("alice123", "alice@example.com");
        mvc.perform(availability("{\"field\":\"email\",\"value\":\" ALICE@EXAMPLE.COM \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.is_available").value(false))
            .andExpect(jsonPath("$.data.normalized_value").value("alice@example.com"));
        mvc.perform(availability("{\"field\":\"login_id\",\"value\":\" alice123 \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.is_available").value(false));
        mvc.perform(availability("{\"field\":\"login_id\",\"value\":\"newuser\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.is_available").value(true))
            .andExpect(header().string("Cache-Control", "private, no-store"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
    }

    @Test void rejectsUnknownFieldsTypesAndInvalidIdentifiers() throws Exception {
        for (String json : List.of(
                "{\"field\":\"login_id\",\"value\":\"User123\"}",
                "{\"field\":\"email\",\"value\":\"invalid-email\"}",
                "{\"field\":\"login_id\",\"value\":1234}",
                "{\"field\":\"login_id\",\"value\":\"alice123\",\"role\":\"ADMIN\"}",
                "{\"field\":\"password\",\"value\":\"secret\"}")) {
            mvc.perform(availability(json)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test void rateLimitIsAtomicAndDoesNotTrustForwardedAddress() throws Exception {
        String json = "{\"field\":\"login_id\",\"value\":\"available123\"}";
        for (int i=0; i<5; i++) mvc.perform(availability(json)).andExpect(status().isOk());
        mvc.perform(availability(json).header("X-Forwarded-For", "203.0.113.9"))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.error.retryable").value(true));
    }

    @Test @Transactional void databaseEnforcesUniqueForeignKeysChecksAndUnsignedRange() {
        long id = user("constraint1", "constraint@example.com");
        assertThatThrownBy(() -> user("constraint1", "other@example.com")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET profile_file_id=999999 WHERE id=?", id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET status='UNKNOWN' WHERE id=?", id))
            .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("ck_users_status");
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET version=-1 WHERE id=?", id)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO notification_settings(user_id,updated_at) VALUES (?,UTC_TIMESTAMP(6))", id);
        assertThatThrownBy(() -> jdbc.update("UPDATE notification_settings SET in_app_enabled=0 WHERE user_id=?", id))
            .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("ck_notification_inapp");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id=?", id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT in_app_enabled FROM notification_settings WHERE user_id=?", Boolean.class, id)).isTrue();
    }

    @Test void concurrentUniqueConstraintAllowsExactlyOneWinner() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0,2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                try { user("raceuser", "race" + index + "@example.com"); return true; }
                catch (DataIntegrityViolationException expected) { return false; }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int winners = 0;
            for (var future : futures) if (future.get(15, TimeUnit.SECONDS)) winners++;
            assertThat(winners).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE login_id='raceuser'", Integer.class)).isEqualTo(1);
        } finally {
            start.countDown();
            jdbc.update("DELETE FROM users WHERE login_id='raceuser'");
        }
    }

    @Test @Transactional void idempotencyKeyIsScopedAndCommittedOnly() {
        long userId = user("idemuser", "idem@example.com");
        String sql = "INSERT INTO idempotency_requests(id,actor_user_id,scope_type,scope_id_snapshot,operation,client_key,payload_hash,status,result_type,result_id_snapshot,response_code,created_at) VALUES (?,?, 'USER',?,'TEST','same-key',?,'COMMITTED','USER',?,200,UTC_TIMESTAMP(6))";
        jdbc.update(sql, UUID.randomUUID().toString(), userId, userId, "a".repeat(64), Long.toString(userId));
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID().toString(), userId, userId, "b".repeat(64), Long.toString(userId)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE idempotency_requests SET status='PENDING' WHERE actor_user_id=?", userId))
            .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("ck_idempotency_committed");
    }

    @Test void concurrentRateLimitAllowsOnlyFiveRequests() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(10)) {
            var futures = java.util.stream.IntStream.range(0,10).mapToObj(index -> executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try { limiter.check("parallel", client); return true; }
                catch (com.gonggeumi.common.web.ApiException exception) {
                    assertThat(exception.status()).isEqualTo(429);
                    return false;
                }
            })).toList();
            start.countDown();
            int allowed = 0;
            for (var future : futures) if (future.get(15, TimeUnit.SECONDS)) allowed++;
            assertThat(allowed).isEqualTo(5);
        } finally { start.countDown(); }
    }

    @Test void unimplementedAuthenticationRemainsClosed() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/auth/signup")).andExpect(status().isForbidden());
    }

    private long user(String loginId, String email) {
        jdbc.update("INSERT INTO users(login_id,email,display_name,created_at,updated_at) VALUES (?,?,'테스터',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", loginId, email);
        return jdbc.queryForObject("SELECT id FROM users WHERE login_id=?", Long.class, loginId);
    }

    private void term(String type, String version, int publishedOffset, Integer retiredOffset) {
        jdbc.update("INSERT INTO terms(term_type,version_name,title,content,is_required,published_at,retired_at) VALUES (?,?,?,'TEST FIXTURE ONLY',?,TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),CASE WHEN ? IS NULL THEN NULL ELSE TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) END)",
                type, version, type + " " + version, !type.equals("MARKETING"), publishedOffset, retiredOffset, retiredOffset);
    }
}
