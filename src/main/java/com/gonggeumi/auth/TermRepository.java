package com.gonggeumi.auth;

import com.gonggeumi.common.domain.UnsignedId;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TermRepository {
    private final JdbcClient jdbc;

    public TermRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<Term> findCurrent(Instant now, BigInteger after, int limit) {
        return jdbc.sql("""
                SELECT t.* FROM terms t
                WHERE t.published_at <= :now AND (t.retired_at IS NULL OR t.retired_at > :now)
                  AND t.id > :after
                  AND NOT EXISTS (
                    SELECT 1 FROM terms newer
                    WHERE newer.term_type = t.term_type
                      AND newer.published_at <= :now
                      AND (newer.retired_at IS NULL OR newer.retired_at > :now)
                      AND (newer.published_at > t.published_at
                           OR (newer.published_at = t.published_at AND newer.id > t.id))
                  )
                ORDER BY t.id ASC LIMIT :limit
                """)
                .param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("after", new BigDecimal(after))
                .param("limit", limit)
                .query((rs, row) -> new Term(
                        new UnsignedId(rs.getBigDecimal("id").toBigIntegerExact()).toString(),
                        rs.getString("term_type"), rs.getString("version_name"), rs.getString("title"),
                        rs.getString("content"), rs.getBoolean("is_required"),
                        rs.getObject("published_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)))
                .list();
    }
}
