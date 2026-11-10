package org.cttelsamicsterrassa.data.load.shared.match;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Region;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionResolver;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;

/**
 * FEAT-00121: the {@code region} / {@code regionType} a match is stored with. The acta payload
 * ({@code region}, {@code tipo_region}) wins; a missing field is derived from the source and the
 * competition by {@link RegionResolver}, and stays {@code null} when neither knows it. Both are
 * excluded from the content checksum, so setting them never reports a match as amended.
 */
public final class MatchRegions {

    private MatchRegions() {
    }

    public static Region region(ImportSource source, Acta acta, String competition) {
        return Region.fromValue(acta.region())
                .or(() -> RegionResolver.resolve(source, competition).map(RegionResolver.Resolution::region))
                .orElse(null);
    }

    public static RegionType regionType(ImportSource source, Acta acta, String competition) {
        return RegionType.fromValue(acta.regionType())
                .or(() -> RegionResolver.resolve(source, competition).map(RegionResolver.Resolution::regionType))
                .orElse(null);
    }
}
