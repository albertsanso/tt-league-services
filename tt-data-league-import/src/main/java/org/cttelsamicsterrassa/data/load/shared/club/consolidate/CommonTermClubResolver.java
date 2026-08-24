package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds conservative groups whose team names share one meaningful terminal term.
 */
@Component
public class CommonTermClubResolver {

    private static final Set<String> IGNORED_TERMS = Set.of(
            "A", "B", "C", "CLUB", "CTT", "TT", "DE", "DEL", "DELS", "LA", "LES", "ELS");

    private final ClubNameNormalizer normalizer;

    public CommonTermClubResolver() {
        this(new ClubNameNormalizer());
    }

    public CommonTermClubResolver(ClubNameNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public List<CommonTermGroup> resolve(ImportSource source, List<Team> teams) {
        Map<String, List<Team>> teamsByTerm = new LinkedHashMap<>();
        for (Team team : teams) {
            String normalizedName = normalizer.exactKey(source, team.getName());
            String terminalTerm = terminalMeaningfulTerm(normalizedName);
            if (terminalTerm != null) {
                teamsByTerm.computeIfAbsent(terminalTerm, ignored -> new java.util.ArrayList<>()).add(team);
            }
        }

        return teamsByTerm.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> new CommonTermGroup(
                        entry.getKey(),
                        "TT " + entry.getKey().toUpperCase(Locale.ROOT),
                        List.copyOf(entry.getValue())))
                .sorted(Comparator.comparing(CommonTermGroup::normalizedTerm))
                .toList();
    }

    private static String terminalMeaningfulTerm(String normalizedName) {
        if (normalizedName.isBlank()) {
            return null;
        }
        List<String> terms = Arrays.stream(normalizedName.split("\\s+"))
                .filter(term -> !term.isBlank())
                .toList();
        if (terms.isEmpty()) {
            return null;
        }
        String terminalTerm = terms.getLast();
        return IGNORED_TERMS.contains(terminalTerm.toUpperCase(Locale.ROOT))
                ? null
                : terminalTerm;
    }

    public record CommonTermGroup(String normalizedTerm, String canonicalDisplayName, List<Team> members) {
    }
}
