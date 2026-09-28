package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable ledger of the fixtures one snapshot run has seen (FEAT-00086). The processors record
 * every dispatched fixture into {@link ImportRunContext#recordSnapshotFixture} before team
 * resolution, the identity guard and the writer; snapshot reconciliation then uses this value to
 * decide which stored SCHEDULED matches were absent from the snapshot.
 *
 * <p>Matching uses the exact values the processors store: the source fixture id ({@code id_partido})
 * and the natural key exactly as {@code findMatchByNaturalKey} uses it. The highest round per
 * competition/group/phase scope implements the FCTT sliding-window rule: stored rounds beyond the
 * snapshot's highest round are not flagged.</p>
 */
public record SnapshotFixtures(Set<String> fixtureIds,
                               Set<NaturalKey> naturalKeys,
                               Map<Scope, Integer> highestRoundByScope) {

    public SnapshotFixtures {
        fixtureIds = fixtureIds == null ? Set.of() : Set.copyOf(fixtureIds);
        naturalKeys = naturalKeys == null ? Set.of() : Set.copyOf(naturalKeys);
        highestRoundByScope = highestRoundByScope == null ? Map.of() : Map.copyOf(highestRoundByScope);
    }

    /** A seen fixture's natural key: the same values {@code findMatchByNaturalKey} uses. */
    public record NaturalKey(String competition, Integer groupNumber, String phase, int round,
                             UUID homeTeamId, UUID awayTeamId) {
    }

    /** The competition/group/phase scope the highest seen round is tracked for (null-safe). */
    public record Scope(String competition, Integer groupNumber, String phase) {
    }

    /** True when the run recorded no fixture at all; reconciliation is then skipped entirely. */
    public boolean isEmpty() {
        return fixtureIds.isEmpty() && naturalKeys.isEmpty() && highestRoundByScope.isEmpty();
    }

    public boolean containsFixtureId(String sourceFixtureId) {
        return sourceFixtureId != null && fixtureIds.contains(sourceFixtureId);
    }

    /**
     * True when the match's natural key was seen. Only fixtures whose teams both resolved were
     * recorded with a natural key, so a match with a missing team never matches here; such fixtures
     * are matched by their fixture id instead.
     */
    public boolean containsNaturalKey(Match match) {
        Objects.requireNonNull(match, "match");
        return naturalKeys.contains(new NaturalKey(match.getCompetition(), match.getGroupNumber(),
                match.getPhase(), match.getRound(),
                match.getHomeTeam() == null ? null : match.getHomeTeam().getId(),
                match.getAwayTeam() == null ? null : match.getAwayTeam().getId()));
    }

    /** The highest round seen in one competition/group/phase scope, if the scope was seen at all. */
    public OptionalInt highestRound(String competition, Integer groupNumber, String phase) {
        Integer highest = highestRoundByScope.get(new Scope(competition, groupNumber, phase));
        return highest == null ? OptionalInt.empty() : OptionalInt.of(highest);
    }

    /** The highest round seen anywhere in the snapshot, for scopes that vanished entirely. */
    public OptionalInt highestRoundOverall() {
        return highestRoundByScope.values().stream().mapToInt(Integer::intValue).max();
    }
}
