package org.cttelsamicsterrassa.data.load.shared.match.backfill;

import org.cttelsamicsterrassa.data.core.domain.match.model.MatchRegionCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRegionBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * FEAT-00121 backfill: sets {@code region} / {@code regionType} on the stored matches of one source
 * that lack them, deriving both from the source and the stored competition with {@link RegionResolver}.
 * A match whose competition the resolver does not know is reported and never written. Opt-in and
 * source-scoped; there is no catch-and-continue, repository failures propagate.
 */
@Component
public class MatchRegionBackfillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchRegionBackfillService.class);

    private final MatchRegionBackfillRepository repository;

    public MatchRegionBackfillService(MatchRegionBackfillRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public MatchRegionBackfillSummary run(ImportSource source, MatchRegionBackfillMode mode) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        LOGGER.info("Starting region backfill for {} in {} mode", source, mode);

        Map<RegionResolver.Resolution, List<MatchRegionCandidate>> resolved = new LinkedHashMap<>();
        List<MatchRegionCandidate> unresolved = new ArrayList<>();
        for (MatchRegionCandidate candidate : repository.findMatchesWithoutRegion(source)) {
            Optional<RegionResolver.Resolution> resolution = RegionResolver.resolve(source, candidate.competition());
            if (resolution.isPresent()) {
                resolved.computeIfAbsent(resolution.get(), key -> new ArrayList<>()).add(candidate);
            } else {
                unresolved.add(candidate);
                LOGGER.warn("Region backfill: no region for {} match {} (competition '{}'); left unchanged",
                        source, candidate.matchId(), candidate.competition());
            }
        }
        resolved.forEach((resolution, matches) -> LOGGER.info("Region backfill: {} {} match(es) -> {}/{}",
                source, matches.size(), resolution.region().value(), resolution.regionType().value()));

        int updated = 0;
        if (mode == MatchRegionBackfillMode.WRITE) {
            for (Map.Entry<RegionResolver.Resolution, List<MatchRegionCandidate>> entry : resolved.entrySet()) {
                List<UUID> ids = entry.getValue().stream().map(MatchRegionCandidate::matchId).toList();
                updated += repository.setRegion(source, entry.getKey().region(), entry.getKey().regionType(), ids);
            }
        }
        MatchRegionBackfillSummary summary = new MatchRegionBackfillSummary(
                source, mode, resolved, List.copyOf(unresolved), updated);
        LOGGER.info("Region backfill for {} in {} mode: {} candidate(s), {} unresolved, {} updated",
                source, mode, summary.candidates(), unresolved.size(), updated);
        return summary;
    }
}
