package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Mirror of the import's BCNESA folder-to-stored-name table: an {@code rtb-*} folder maps to the legacy competition
 * name, any other folder is stored unchanged. Comparison is case-insensitive on the folder and exact on the name; an
 * unmapped {@code rtb-*} folder has no name. A change to the import's table must update the defaults here.
 */
public final class BcnesaCompetitionNames {

    private static final String EXPORT_PREFIX = "rtb-";

    private final Map<String, String> byFolder;

    public BcnesaCompetitionNames(Map<String, String> byFolder) {
        Map<String, String> copy = new LinkedHashMap<>();
        byFolder.forEach((folder, name) -> {
            if (folder == null || folder.isBlank() || name == null || name.isBlank()) {
                throw new IllegalArgumentException("BCNESA competition names need a folder and a name");
            }
            copy.put(folder.trim().toLowerCase(Locale.ROOT), name);
        });
        this.byFolder = Map.copyOf(copy);
    }

    public static BcnesaCompetitionNames defaults() {
        return new BcnesaCompetitionNames(defaultEntries());
    }

    public static Map<String, String> defaultEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("rtb-preferent", "Preferent");
        entries.put("rtb-primera", "Primera");
        entries.put("rtb-segona-a", "Segona _A_");
        entries.put("rtb-segona-b", "Segona _B_");
        entries.put("rtb-tercera-a", "Tercera _A_");
        entries.put("rtb-tercera-b", "Tercera _B_");
        entries.put("rtb-1a-comarcal", "1a Comarcal");
        entries.put("rtb-2a-comarcal", "2a Comarcal");
        entries.put("rtb-veterans-1a", "Vet 1a");
        entries.put("rtb-veterans-2aa", "Vet 2a _A_");
        entries.put("rtb-veterans-2ab", "Vet 2a _B_");
        entries.put("rtb-veterans-3a-a", "Vet 3a _A_");
        entries.put("rtb-veterans-3a-b", "Vet 3a _B_");
        entries.put("rtb-veterans-4a-a", "Vet 4a _A_");
        entries.put("rtb-veterans-4a-b", "Vet 4a _B_");
        entries.put("rtb-veterans-4a-c", "Vet 4a _C_");
        return entries;
    }

    public Optional<String> storedName(String competitionFolder) {
        if (competitionFolder.regionMatches(true, 0, EXPORT_PREFIX, 0, EXPORT_PREFIX.length())) {
            return Optional.ofNullable(byFolder.get(competitionFolder.toLowerCase(Locale.ROOT)));
        }
        return Optional.of(competitionFolder);
    }
}
