package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable state owned by one import invocation. It is deliberately not static or shared between
 * requests; processors may use it for exact-key lookup memoization as the execution pipeline grows.
 */
public final class ImportRunContext {
    private final ImportSource source;
    private final String season;
    private final Map<Object, Object> lookups = new HashMap<>();

    public ImportRunContext(ImportSource source, String season) {
        this.source = Objects.requireNonNull(source, "source");
        this.season = season;
    }

    public ImportSource source() {
        return source;
    }

    public String season() {
        return season;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Object key) {
        return (T) lookups.get(key);
    }

    public void put(Object key, Object value) {
        lookups.put(Objects.requireNonNull(key, "key"), value);
    }

    public int size() {
        return lookups.size();
    }
}
