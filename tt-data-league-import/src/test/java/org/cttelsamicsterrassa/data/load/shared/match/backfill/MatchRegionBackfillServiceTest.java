package org.cttelsamicsterrassa.data.load.shared.match.backfill;

import org.cttelsamicsterrassa.data.core.domain.match.model.MatchRegionCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRegionBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Region;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionResolver;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchRegionBackfillServiceTest {

    private final FakeRepository repository = new FakeRepository(
            candidate("Preferent"), candidate("1a Comarcal"), candidate("rtg-preferent"), candidate("rtx-unknown"),
            candidate(null));
    private final MatchRegionBackfillService service = new MatchRegionBackfillService(repository);

    @Test
    void reportModeAnalysesButNeverWrites() {
        MatchRegionBackfillSummary summary = service.run(ImportSource.BCNESA, MatchRegionBackfillMode.REPORT);

        assertEquals(5, summary.candidates());
        assertEquals(2, summary.unresolved().size());
        assertEquals(0, summary.updated());
        assertTrue(repository.writes.isEmpty());
        assertEquals(3, summary.resolved().size());
    }

    @Test
    void writeModeSetsEachResolutionOnItsMatchesAndSkipsUnresolvedOnes() {
        MatchRegionBackfillSummary summary = service.run(ImportSource.BCNESA, MatchRegionBackfillMode.WRITE);

        assertEquals(3, summary.updated());
        assertEquals(3, repository.writes.size());
        assertEquals(List.of(new Write(ImportSource.BCNESA, Region.BARCELONA, RegionType.PROVINCIAL, 1),
                        new Write(ImportSource.BCNESA, Region.BARCELONA, RegionType.COMARCAL, 1),
                        new Write(ImportSource.BCNESA, Region.GIRONA, RegionType.PROVINCIAL, 1)),
                repository.writes);
        assertEquals(2, summary.unresolved().size());
    }

    @Test
    void reportAndWriteModesAgreeOnTheAnalysis() {
        MatchRegionBackfillSummary report = service.run(ImportSource.BCNESA, MatchRegionBackfillMode.REPORT);
        MatchRegionBackfillSummary write = service.run(ImportSource.BCNESA, MatchRegionBackfillMode.WRITE);

        assertEquals(report.resolved(), write.resolved());
        assertEquals(report.unresolved(), write.unresolved());
    }

    @Test
    void sourcesWithAFixedRegionResolveEveryCandidate() {
        FakeRepository fctt = new FakeRepository(candidate("tercera-nacional"), candidate(null));
        MatchRegionBackfillSummary summary =
                new MatchRegionBackfillService(fctt).run(ImportSource.FCTT, MatchRegionBackfillMode.WRITE);

        assertEquals(2, summary.updated());
        assertEquals(List.of(new Write(ImportSource.FCTT, Region.CATALUNYA, RegionType.AUTONOMICA, 2)), fctt.writes);
        assertEquals(new RegionResolver.Resolution(Region.CATALUNYA, RegionType.AUTONOMICA),
                summary.resolved().keySet().iterator().next());
    }

    private static MatchRegionCandidate candidate(String competition) {
        return new MatchRegionCandidate(UUID.randomUUID(), competition);
    }

    private record Write(ImportSource source, Region region, RegionType regionType, int matches) {
    }

    private static final class FakeRepository implements MatchRegionBackfillRepository {
        private final List<MatchRegionCandidate> candidates;
        private final List<Write> writes = new ArrayList<>();

        private FakeRepository(MatchRegionCandidate... candidates) {
            this.candidates = List.of(candidates);
        }

        @Override
        public List<MatchRegionCandidate> findMatchesWithoutRegion(ImportSource source) {
            return candidates;
        }

        @Override
        public int setRegion(ImportSource source, Region region, RegionType regionType, Collection<UUID> matchIds) {
            writes.add(new Write(source, region, regionType, matchIds.size()));
            return matchIds.size();
        }
    }
}
