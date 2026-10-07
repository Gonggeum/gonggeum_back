package com.gonggeumi.auth;

import com.gonggeumi.common.web.ApiException;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermService {
    private final TermRepository repository;
    private final TermCursor cursor;
    private final Clock clock;

    public TermService(TermRepository repository, TermCursor cursor, Clock clock) {
        this.repository = repository;
        this.cursor = cursor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TermPage current(String token, int limit) {
        if (limit < 1 || limit > 100) throw ApiException.invalid("limit", "1~100 사이의 정수를 입력해 주세요.");
        var rows = repository.findCurrent(clock.instant(), cursor.read(token, limit), limit + 1);
        boolean hasNext = rows.size() > limit;
        List<Term> items = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        String next = hasNext ? cursor.issue(items.getLast().id(), limit) : null;
        return new TermPage(items, new Page(next, hasNext, limit));
    }

    public record Page(String nextCursor, boolean hasNext, int limit) {}
    public record TermPage(List<Term> items, Page page) {}
}
