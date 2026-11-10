package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import java.util.UUID;

/**
 * JPQL constructor-expression projection for {@link MatchRepositoryHelper#findMatchesWithoutRegion}.
 * Mapped to the domain {@code MatchRegionCandidate} by the JPA adapter.
 */
public class MatchRegionCandidateProjection {

    private final UUID matchId;
    private final String competition;

    public MatchRegionCandidateProjection(UUID matchId, String competition) {
        this.matchId = matchId;
        this.competition = competition;
    }

    public UUID matchId() {
        return matchId;
    }

    public String competition() {
        return competition;
    }
}
