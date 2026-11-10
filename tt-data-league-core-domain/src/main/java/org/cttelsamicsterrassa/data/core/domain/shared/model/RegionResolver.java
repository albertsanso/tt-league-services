package org.cttelsamicsterrassa.data.core.domain.shared.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Derives the {@link Region} and {@link RegionType} of a competition from its source and its folder
 * or stored name. Pure and closed: no fuzzy matching, and unknown input yields an empty result, never
 * a default.
 *
 * <ul>
 *   <li>RFETM: {@code espana} / {@code nacional}.</li>
 *   <li>FCTT: {@code catalunya} / {@code autonomica} (TDM, Copa Catalana Femenina).</li>
 *   <li>BCNESA: an export folder {@code rtb-}, {@code rtg-}, {@code rtl-} or {@code rtt-} names its
 *       province; any other name is a legacy display-name folder ({@code Preferent}, {@code Vet 1a}),
 *       and legacy exports are Barcelona-only. A name containing {@code comarcal} is {@code comarcal},
 *       any other {@code provincial}. An {@code rt?-} prefix outside that list has no region.</li>
 * </ul>
 */
public final class RegionResolver {

    /** A resolved pair. */
    public record Resolution(Region region, RegionType regionType) {
    }

    private static final Map<String, Region> BY_EXPORT_PREFIX = Map.of(
            "rtb-", Region.BARCELONA,
            "rtg-", Region.GIRONA,
            "rtl-", Region.LLEIDA,
            "rtt-", Region.TARRAGONA);

    private RegionResolver() {
    }

    /**
     * The region of a competition of {@code source}. {@code competition} is the export folder or the
     * stored competition name; it is only read for BCNESA.
     */
    public static Optional<Resolution> resolve(ImportSource source, String competition) {
        if (source == null) {
            return Optional.empty();
        }
        return switch (source) {
            case RFETM -> Optional.of(new Resolution(Region.ESPANA, RegionType.NACIONAL));
            case FCTT -> Optional.of(new Resolution(Region.CATALUNYA, RegionType.AUTONOMICA));
            case BCNESA -> resolveBcnesa(competition);
        };
    }

    private static Optional<Resolution> resolveBcnesa(String competition) {
        if (competition == null || competition.isBlank()) {
            return Optional.empty();
        }
        String name = competition.trim().toLowerCase(Locale.ROOT);
        Region region = null;
        for (Map.Entry<String, Region> prefix : BY_EXPORT_PREFIX.entrySet()) {
            if (name.startsWith(prefix.getKey())) {
                region = prefix.getValue();
            }
        }
        if (region == null) {
            if (name.matches("rt[a-z]-.*")) {
                return Optional.empty();
            }
            region = Region.BARCELONA;
        }
        boolean comarcal = fold(name).contains("comarcal");
        return Optional.of(new Resolution(region, comarcal ? RegionType.COMARCAL : RegionType.PROVINCIAL));
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
