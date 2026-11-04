package org.cttelsamicsterrassa.data.core.application.importjob;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lists import jobs, most recent first. {@code from} and {@code to} are inclusive UTC dates on the job creation
 * time. The constructor validates the filters and throws {@link IllegalArgumentException} for invalid ones.
 */
public class FindImportJobHistoryQuery extends DomainQuery {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private final Optional<ImportSource> source;
    private final Optional<LocalDate> from;
    private final Optional<LocalDate> to;
    private final int limit;

    public FindImportJobHistoryQuery(Optional<String> source, Optional<LocalDate> from, Optional<LocalDate> to,
                                     Optional<Integer> limit) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.source = Objects.requireNonNull(source, "source").map(FindImportJobHistoryQuery::parseSource);
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.limit = Objects.requireNonNull(limit, "limit").orElse(DEFAULT_LIMIT);
        if (this.limit < 1 || this.limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (this.from.isPresent() && this.to.isPresent() && this.from.get().isAfter(this.to.get())) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }

    private static ImportSource parseSource(String value) {
        try {
            return ImportSource.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("source must be one of " + Arrays.toString(ImportSource.values()),
                    exception);
        }
    }

    public Optional<ImportSource> getSource() {
        return source;
    }

    public Optional<LocalDate> getFrom() {
        return from;
    }

    public Optional<LocalDate> getTo() {
        return to;
    }

    public int getLimit() {
        return limit;
    }
}
