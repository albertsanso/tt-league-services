package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import javax.inject.Named;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Named
public class ClubNameNormalizer {

    public String exactKey(ImportSource source, String name) {
        String display = toDisplayCandidate(source, name);
        String withoutAccents = stripAccents(display).toLowerCase(Locale.ROOT);
        String compact = withoutAccents
                .replaceAll("[^\\p{Alnum}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (source == ImportSource.BCNESA) {
            compact = compact.replace(
                    "cett sant andreu de la barca",
                    "cett sant andreu la barca");
        }
        return compact;
    }

    public String preferredDisplayName(ImportSource source, List<String> names) {
        Objects.requireNonNull(names, "names must not be null");
        return names.stream()
                .map(name -> toDisplayCandidate(source, name))
                .filter(candidate -> !candidate.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElse("");
    }

    private String toDisplayCandidate(ImportSource source, String raw) {
        if (raw == null) {
            return "";
        }

        String normalized = raw
                .replace('´', '\'')
                .replace('`', '\'')
                .replace('’', '\'')
                .toUpperCase(Locale.ROOT)
                .replace(".", "")
                .replace("'", "")
                .replace("\"", "")
                .replaceAll("[,;:()\\[\\]{}]", " ")
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (source == ImportSource.BCNESA) {
            normalized = normalized
                    .replaceFirst("^À?NECBLAU\\s+", "")
                    .replaceAll("\\bTT\\s+ELS\\s+JOVES\\b", "TT JOVES CTDFELS")
                    .replaceAll("\\bST\\b", "SANT")
                    .replaceAll("\\s+LA\\s+CASSOLA$", "")
                    .replaceAll("^CTT\\s+AMICS\\s+(?:DE\\s+)?TERRASSA$", "CTT ELS AMICS DE TERRASSA")
                    .replaceAll("^CTT\\s+ELS\\s+AMICS\\s+TERRASSA$", "CTT ELS AMICS DE TERRASSA");
        }

        normalized = normalized
                .replaceAll("\\bT\\s*T\\b", "TT")
                .replaceAll("\\s+(?:SEN|VET)\\s+[A-Z](?:\\s+[A-Z])?$", "")
                .replaceAll("\\s+[A-Z]\\s+(?:SEN|VET)$", "")
                .replaceAll("\\s+[ABC]$", "")
                .replaceAll("\\s+TT$", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (source == ImportSource.BCNESA) {
            normalized = normalized
                    .replaceAll("^CTT\\s+AMICS\\s+(?:DE\\s+)?TERRASSA$", "CTT ELS AMICS DE TERRASSA")
                    .replaceAll("^CTT\\s+ELS\\s+AMICS\\s+TERRASSA$", "CTT ELS AMICS DE TERRASSA");
        }

        return normalized;
    }

    private static String stripAccents(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }
}
