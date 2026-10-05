package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;

/**
 * Identity of one ingest group without its match days, limited to the fields the source supports. The scope key
 * excludes the rounds, so back-off state survives a new round being added.
 */
public record PollUnit(String category, String group, String phase, String territory, String gender) {

    public String scopeKey() {
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

    public ScopeFilter filter(List<Integer> matchDays) {
        return new ScopeFilter(category, group, phase, territory, gender, matchDays);
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
