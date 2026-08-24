package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts stable canonical names from terms shared by a cluster of source names.
 */
public class FederatedClubClusterLabeler {

    private static final Set<String> STOP_WORDS = Set.of(
            "A", "B", "C", "CLUB", "CTT", "DE", "DEL", "DELS", "ELS", "LA", "LES",
            "MESA", "TAULA", "TENIS", "TENNIS", "TT");

    private final ClubNameNormalizer normalizer;

    public FederatedClubClusterLabeler(ClubNameNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public String label(ImportSource source, List<FederatedClub> clubs) {
        String preferredName = normalizer.preferredDisplayName(
                source, clubs.stream().map(FederatedClub::getName).toList());
        if (clubs.size() < 2) {
            return preferredName;
        }

        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (FederatedClub club : clubs) {
            Arrays.stream(normalizer.exactKey(source, club.getName()).split("\\s+"))
                    .filter(term -> !term.isBlank())
                    .map(term -> term.toUpperCase(Locale.ROOT))
                    .filter(term -> !STOP_WORDS.contains(term))
                    .forEach(term -> frequencies.merge(term, 1, Integer::sum));
        }

        int minimumFrequency = (clubs.size() + 1) / 2;
        List<String> commonTerms = frequencies.entrySet().stream()
                .filter(entry -> entry.getValue() >= minimumFrequency)
                .map(Map.Entry::getKey)
                .toList();
        if (commonTerms.isEmpty()) {
            return preferredName;
        }
        Set<String> commonTermSet = Set.copyOf(commonTerms);
        return Arrays.stream(normalizer.exactKey(source, preferredName).split("\\s+"))
                .map(term -> term.toUpperCase(Locale.ROOT))
                .filter(commonTermSet::contains)
                .distinct()
                .collect(Collectors.joining(" "));
    }
}
