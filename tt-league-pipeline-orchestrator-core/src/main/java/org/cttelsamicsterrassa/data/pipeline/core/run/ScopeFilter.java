package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.util.List;

/** One scope selection, mirroring the ingest scope contract. */
public record ScopeFilter(
        String category, String group, String phase, String territory, String gender, List<Integer> matchDays) {

    public ScopeFilter {
        category = optional(category, "category");
        group = optional(group, "group");
        phase = optional(phase, "phase");
        territory = optional(territory, "territory");
        gender = optional(gender, "gender");
        matchDays = matchDays == null ? List.of() : List.copyOf(matchDays);
        for (Integer matchDay : matchDays) {
            if (matchDay == null || matchDay < 1) {
                throw new IllegalArgumentException("matchDays must contain positive numbers only");
            }
        }
        if (category == null && group == null && phase == null && territory == null && gender == null
                && matchDays.isEmpty()) {
            throw new IllegalArgumentException("A scope filter must set at least one field");
        }
    }

    private static String optional(String value, String name) {
        return value == null ? null : Checks.nonBlank(value, name);
    }
}
