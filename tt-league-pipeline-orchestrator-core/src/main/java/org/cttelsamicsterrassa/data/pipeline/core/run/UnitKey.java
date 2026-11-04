package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable identity of a run unit: the SHA-256 of the canonical ingest-group identity, without its match days. The
 * hash is byte-identical to the poll schedule scope key, so poll schedules, statistics and alerts refer to the same
 * unit. A full-season unit uses {@link #SEASON}; a backfilled multi-filter legacy unit uses {@link #LEGACY}.
 */
public final class UnitKey {

    public static final String SEASON = "season";
    public static final String LEGACY = "legacy";

    private UnitKey() {
    }

    public static String of(String category, String group, String phase, String territory, String gender) {
        String canonical = String.join("|",
                "category=" + empty(category), "group=" + empty(group), "phase=" + empty(phase),
                "territory=" + empty(territory), "gender=" + empty(gender));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** The key of one scope filter; its match days are not part of the identity. */
    public static String of(ScopeFilter filter) {
        Checks.required(filter, "filter");
        return of(filter.category(), filter.group(), filter.phase(), filter.territory(), filter.gender());
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
