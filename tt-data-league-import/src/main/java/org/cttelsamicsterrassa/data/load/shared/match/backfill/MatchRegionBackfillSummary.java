package org.cttelsamicsterrassa.data.load.shared.match.backfill;

import org.cttelsamicsterrassa.data.core.domain.match.model.MatchRegionCandidate;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionResolver;

import java.util.List;
import java.util.Map;

/**
 * Result of one {@link MatchRegionBackfillService} run.
 *
 * @param resolved   candidates whose region could be derived, grouped by the derived region pair
 * @param unresolved candidates whose competition the resolver does not know; never written
 * @param updated    matches updated; always {@code 0} in {@link MatchRegionBackfillMode#REPORT}
 */
public record MatchRegionBackfillSummary(
        ImportSource source,
        MatchRegionBackfillMode mode,
        Map<RegionResolver.Resolution, List<MatchRegionCandidate>> resolved,
        List<MatchRegionCandidate> unresolved,
        int updated) {

    public int candidates() {
        return resolved.values().stream().mapToInt(List::size).sum() + unresolved.size();
    }
}
