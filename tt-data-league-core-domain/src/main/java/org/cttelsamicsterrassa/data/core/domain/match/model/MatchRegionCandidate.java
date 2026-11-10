package org.cttelsamicsterrassa.data.core.domain.match.model;

import java.util.UUID;

/**
 * A stored match that has no {@code region} or {@code regionType} yet (FEAT-00121), with the only
 * field the {@link org.cttelsamicsterrassa.data.core.domain.shared.model.RegionResolver} reads.
 */
public record MatchRegionCandidate(UUID matchId, String competition) {
}
