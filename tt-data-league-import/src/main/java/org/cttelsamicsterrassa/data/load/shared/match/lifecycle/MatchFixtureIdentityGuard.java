package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.Objects;
import java.util.Optional;

/**
 * Cross-checks an incoming acta's source fixture id ({@code id_partido}) against the match its
 * natural key resolves to, before anything is written (FEAT-00085, analysis gaps G8/G16, risk K4).
 *
 * <p>A jornada drift empties the natural-key lookup for a fixture that is already stored under the
 * same {@code id_partido}; without this guard the writer would then either fail on the
 * {@code uk_match_source_fixture_id} constraint or silently create a duplicate. The guard turns
 * that situation into a reported conflict instead: the natural key stays the primary identity of
 * the write path and {@code id_partido} is only a consistency check.</p>
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>incoming fixture id {@code null} (legacy file, BCNESA fixture index &gt; 0): no conflict,
 *       no lookup;</li>
 *   <li>stored by fixture id, no natural-key match: conflict (the drift case);</li>
 *   <li>stored by fixture id and by natural key but different matches: conflict;</li>
 *   <li>no fixture-id match, natural-key match keeps a different non-null fixture id: conflict
 *       (closes the upgrade overwrite FEAT-00083 deferred here);</li>
 *   <li>otherwise (same match found by both, natural-key match with a {@code null} stored fixture
 *       id, or nothing found by either): no conflict.</li>
 * </ol>
 *
 * <p>Plain class constructed by each match processor with its {@link MatchRepository}, like
 * {@link MatchLifecycleWriter}; it never writes, never throws for a conflict, and lets repository
 * failures propagate. It does not build a {@link Match}: a throwaway {@code createNew()} header
 * would publish a {@code MatchCreatedEvent}.</p>
 */
public final class MatchFixtureIdentityGuard {

    /**
     * The values the processor used for {@code findMatchByNaturalKey} plus the fixture id it would
     * store. {@code source}, {@code competition} and {@code season} are required; {@code phase} and
     * the fixture id may be {@code null}.
     */
    public record IncomingFixture(ImportSource source, String sourceFixtureId, String competition,
                                  Season season, Integer groupNumber, int round, String phase) {

        public IncomingFixture {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(competition, "competition");
            Objects.requireNonNull(season, "season");
        }
    }

    private final MatchRepository matchRepository;

    public MatchFixtureIdentityGuard(MatchRepository matchRepository) {
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
    }

    /**
     * Whether the incoming fixture may proceed to {@link MatchLifecycleWriter}.
     *
     * @return a human-readable conflict reason for the reported issue, or empty when the write may
     *         proceed
     */
    public Optional<String> conflict(IncomingFixture incoming, Optional<Match> naturalKeyMatch) {
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(naturalKeyMatch, "naturalKeyMatch");

        if (incoming.sourceFixtureId() == null) {
            return Optional.empty();
        }
        Optional<Match> byFixture =
                matchRepository.findBySourceFixtureId(incoming.source(), incoming.sourceFixtureId());

        if (byFixture.isPresent() && naturalKeyMatch.isEmpty()) {
            return Optional.of("id_partido " + incoming.sourceFixtureId() + " is already stored on "
                    + describe(byFixture.get()) + " but this acta's natural key is "
                    + describeIncoming(incoming)
                    + ": the fixture drifted; not stored to avoid a duplicate fixture");
        }
        if (byFixture.isPresent() && naturalKeyMatch.isPresent()
                && !byFixture.get().getId().equals(naturalKeyMatch.get().getId())) {
            return Optional.of("id_partido " + incoming.sourceFixtureId() + " points at "
                    + describe(byFixture.get()) + " while the natural key points at "
                    + describe(naturalKeyMatch.get())
                    + ": two stored matches disagree; not stored to avoid a duplicate fixture");
        }
        if (byFixture.isEmpty() && naturalKeyMatch.isPresent()) {
            Match stored = naturalKeyMatch.get();
            if (stored.getSourceFixtureId() != null
                    && !stored.getSourceFixtureId().equals(incoming.sourceFixtureId())) {
                return Optional.of("id_partido " + incoming.sourceFixtureId() + " conflicts with the "
                        + describeIncoming(incoming) + " already stored as match " + stored.getId()
                        + " with id_partido " + stored.getSourceFixtureId()
                        + ": not stored to avoid a duplicate fixture");
            }
        }
        return Optional.empty();
    }

    private static String describe(Match match) {
        return "match " + match.getId() + " (competition " + match.getCompetition() + ", group "
                + match.getGroupNumber() + ", round " + match.getRound() + ", phase "
                + match.getPhase() + ")";
    }

    private static String describeIncoming(IncomingFixture incoming) {
        return "competition " + incoming.competition() + ", group " + incoming.groupNumber()
                + ", round " + incoming.round() + ", phase " + incoming.phase();
    }
}
