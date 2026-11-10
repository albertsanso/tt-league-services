package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchRegionCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRegionBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Region;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.RegionMapping;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class MatchRegionBackfillRepositoryJpa implements MatchRegionBackfillRepository {

    /** Keeps every bulk statement under common JDBC bind-parameter limits. */
    private static final int CHUNK_SIZE = 500;

    private final MatchRepositoryHelper matchRepositoryHelper;

    @Override
    public List<MatchRegionCandidate> findMatchesWithoutRegion(ImportSource source) {
        Objects.requireNonNull(source, "source");
        return matchRepositoryHelper.findMatchesWithoutRegion(Source.valueOf(source.name())).stream()
                .map(projection -> new MatchRegionCandidate(projection.matchId(), projection.competition()))
                .toList();
    }

    @Override
    public int setRegion(ImportSource source, Region region, RegionType regionType, Collection<UUID> matchIds) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(regionType, "regionType");
        if (matchIds == null || matchIds.isEmpty()) {
            return 0;
        }
        List<UUID> ids = List.copyOf(matchIds);
        int updated = 0;
        for (int from = 0; from < ids.size(); from += CHUNK_SIZE) {
            List<UUID> chunk = new ArrayList<>(ids.subList(from, Math.min(from + CHUNK_SIZE, ids.size())));
            int changed = matchRepositoryHelper.updateRegion(
                    Source.valueOf(source.name()),
                    RegionMapping.toJpa(region),
                    RegionMapping.toJpa(regionType),
                    chunk);
            if (changed != chunk.size()) {
                throw new IllegalStateException("Expected to set the region of " + chunk.size()
                        + " " + source + " matches but updated " + changed);
            }
            updated += changed;
        }
        return updated;
    }
}
