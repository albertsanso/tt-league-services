package org.cttelsamicsterrassa.data.core.domain.match.repository;

import org.cttelsamicsterrassa.data.core.domain.match.model.MatchRegionCandidate;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Region;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence port of the FEAT-00121 region backfill. Source-scoped like every import lookup.
 */
public interface MatchRegionBackfillRepository {

    /** Matches of {@code source} whose {@code region} or {@code regionType} is still unset. */
    List<MatchRegionCandidate> findMatchesWithoutRegion(ImportSource source);

    /**
     * Sets {@code region} and {@code regionType} on the given matches of {@code source} and nothing
     * else.
     *
     * @return the number of matches updated
     * @throws IllegalStateException if some of the matches no longer exist or belong to another source
     */
    int setRegion(ImportSource source, Region region, RegionType regionType, Collection<UUID> matchIds);
}
