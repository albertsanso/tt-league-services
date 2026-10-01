package org.cttelsamicsterrassa.data.load.bcnesa;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps the competition folder of a BCNESA export to the competition name that is stored.
 *
 * <p>Exports up to 2025-2026 use display-name folders ({@code Preferent}, {@code Vet 1a}), stored
 * unchanged. The 2026-2027 export uses {@code rtb-*} slugs, mapped here by an explicit closed table
 * to the legacy names so every season keeps one competition name. There is no fuzzy matching and no
 * default: an unmapped {@code rtb-*} folder yields an empty result and must not be imported.</p>
 */
public final class BcnesaCompetitionNames {

    private static final String EXPORT_PREFIX = "rtb-";

    private static final Map<String, String> BY_EXPORT_FOLDER = Map.ofEntries(
            Map.entry("rtb-preferent", "Preferent"),
            Map.entry("rtb-primera", "Primera"),
            Map.entry("rtb-segona-a", "Segona _A_"),
            Map.entry("rtb-segona-b", "Segona _B_"),
            Map.entry("rtb-tercera-a", "Tercera _A_"),
            Map.entry("rtb-tercera-b", "Tercera _B_"),
            Map.entry("rtb-1a-comarcal", "1a Comarcal"),
            Map.entry("rtb-2a-comarcal", "2a Comarcal"),
            Map.entry("rtb-veterans-1a", "Vet 1a"),
            Map.entry("rtb-veterans-2aa", "Vet 2a _A_"),
            Map.entry("rtb-veterans-2ab", "Vet 2a _B_"),
            Map.entry("rtb-veterans-3a-a", "Vet 3a _A_"),
            Map.entry("rtb-veterans-3a-b", "Vet 3a _B_"),
            Map.entry("rtb-veterans-4a-a", "Vet 4a _A_"),
            Map.entry("rtb-veterans-4a-b", "Vet 4a _B_"),
            Map.entry("rtb-veterans-4a-c", "Vet 4a _C_"));

    private BcnesaCompetitionNames() {
    }

    /**
     * The stored competition name for {@code competitionFolder}: the mapped name for an {@code rtb-*}
     * folder (empty when unmapped), the folder name unchanged for any other folder.
     */
    public static Optional<String> storedName(String competitionFolder) {
        if (competitionFolder.regionMatches(true, 0, EXPORT_PREFIX, 0, EXPORT_PREFIX.length())) {
            return Optional.ofNullable(BY_EXPORT_FOLDER.get(competitionFolder.toLowerCase(Locale.ROOT)));
        }
        return Optional.of(competitionFolder);
    }
}
