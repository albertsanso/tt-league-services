package org.cttelsamicsterrassa.data.load.shared.match;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Region;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionResolver;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;

import java.util.Optional;

/**
 * FEAT-00121: the {@code region} / {@code regionType} a match is stored with. The acta payload
 * ({@code region}, {@code tipo_region}) wins; a missing field is derived from the source and the
 * competition by {@link RegionResolver}, and stays {@code null} when neither knows it. A payload value
 * that the source cannot have (an RFETM acta in {@code girona}) is rejected with an
 * {@link IllegalArgumentException}, never stored. Both are excluded from the content checksum, so
 * setting them never reports a match as amended.
 */
public final class MatchRegions {

    private MatchRegions() {
    }

    public static Region region(ImportSource source, Acta acta, String competition) {
        Optional<Region> declared = Region.fromValue(acta.region());
        declared.ifPresent(value -> {
            if (!RegionResolver.allows(source, value)) {
                throw new IllegalArgumentException("region '" + value.value() + "' is not valid for source " + source);
            }
        });
        return declared
                .or(() -> RegionResolver.resolve(source, competition).map(RegionResolver.Resolution::region))
                .orElse(null);
    }

    public static RegionType regionType(ImportSource source, Acta acta, String competition) {
        Optional<RegionType> declared = RegionType.fromValue(acta.regionType());
        declared.ifPresent(value -> {
            if (!RegionResolver.allows(source, value)) {
                throw new IllegalArgumentException(
                        "tipo_region '" + value.value() + "' is not valid for source " + source);
            }
        });
        return declared
                .or(() -> RegionResolver.resolve(source, competition).map(RegionResolver.Resolution::regionType))
                .orElse(null);
    }
}
