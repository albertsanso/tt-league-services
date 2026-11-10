package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchRegionCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRegionBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Region;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** FEAT-00121: the nullable {@code region} / {@code region_type} columns and their backfill port. */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchRegionRepositoryJpaTest {

    private static final Season SEASON = Season.of(2026);

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private MatchRegionBackfillRepository backfillRepository;

    @Test
    void regionAndRegionTypeRoundTripAndLegacyRowsReadAsNull() {
        Match withRegion = match(ImportSource.BCNESA, "rtg-1a-comarcal", Region.GIRONA, RegionType.COMARCAL);
        Match legacy = match(ImportSource.BCNESA, "Preferent", null, null);

        Match stored = matchRepository.findMatchById(withRegion.getId()).orElseThrow();
        assertEquals(Region.GIRONA, stored.getRegion());
        assertEquals(RegionType.COMARCAL, stored.getRegionType());
        Match storedLegacy = matchRepository.findMatchById(legacy.getId()).orElseThrow();
        assertNull(storedLegacy.getRegion());
        assertNull(storedLegacy.getRegionType());
    }

    @Test
    void backfillFindsOnlyTheMatchesOfTheSourceWithoutARegionAndSetsOnlyThoseColumns() {
        Match missing = match(ImportSource.BCNESA, "Preferent", null, null);
        Match half = match(ImportSource.BCNESA, "1a Comarcal", Region.BARCELONA, null);
        match(ImportSource.BCNESA, "rtb-preferent", Region.BARCELONA, RegionType.PROVINCIAL);
        Match otherSource = match(ImportSource.FCTT, "tdm", null, null);

        List<MatchRegionCandidate> candidates = backfillRepository.findMatchesWithoutRegion(ImportSource.BCNESA);
        assertEquals(List.of(missing.getId(), half.getId()).stream().sorted().toList(),
                candidates.stream().map(MatchRegionCandidate::matchId).sorted().toList());

        int updated = backfillRepository.setRegion(ImportSource.BCNESA, Region.BARCELONA, RegionType.COMARCAL,
                List.of(half.getId()));
        assertEquals(1, updated);

        Match changed = matchRepository.findMatchById(half.getId()).orElseThrow();
        assertEquals(RegionType.COMARCAL, changed.getRegionType());
        assertEquals("1a Comarcal", changed.getCompetition());
        assertEquals(half.getHomeTeam().getId(), changed.getHomeTeam().getId());
        assertEquals(1, backfillRepository.findMatchesWithoutRegion(ImportSource.BCNESA).size());
        assertEquals(1, backfillRepository.findMatchesWithoutRegion(ImportSource.FCTT).size());
        assertNull(matchRepository.findMatchById(otherSource.getId()).orElseThrow().getRegion());
    }

    @Test
    void backfillFillsOnlyMissingColumnsAndNeverOverwritesAValueThatIsSet() {
        Match half = match(ImportSource.BCNESA, "rtg-preferent", Region.GIRONA, null);
        Match complete = match(ImportSource.BCNESA, "rtb-preferent", Region.BARCELONA, RegionType.PROVINCIAL);

        assertEquals(1, backfillRepository.setRegion(ImportSource.BCNESA, Region.BARCELONA, RegionType.COMARCAL,
                List.of(half.getId())));
        Match filled = matchRepository.findMatchById(half.getId()).orElseThrow();
        assertEquals(Region.GIRONA, filled.getRegion());
        assertEquals(RegionType.COMARCAL, filled.getRegionType());

        assertThrows(IllegalStateException.class, () -> backfillRepository.setRegion(
                ImportSource.BCNESA, Region.GIRONA, RegionType.COMARCAL, List.of(complete.getId())));
        Match untouched = matchRepository.findMatchById(complete.getId()).orElseThrow();
        assertEquals(Region.BARCELONA, untouched.getRegion());
        assertEquals(RegionType.PROVINCIAL, untouched.getRegionType());
    }

    @Test
    void searchAndCountFilterByRegionAndRegionTypeAndIgnoreThemWhenAbsent() {
        Match girona = match(ImportSource.BCNESA, "rtg-1a-comarcal", Region.GIRONA, RegionType.COMARCAL);
        Match barcelona = match(ImportSource.BCNESA, "rtb-preferent", Region.BARCELONA, RegionType.PROVINCIAL);
        Match legacy = match(ImportSource.BCNESA, "Preferent", null, null);

        assertEquals(3, matchRepository.countMatches(criteria(null, null)));
        assertEquals(List.of(girona.getId()), searchIds(criteria(Region.GIRONA, null)));
        assertEquals(1, matchRepository.countMatches(criteria(Region.GIRONA, null)));
        assertEquals(List.of(barcelona.getId()), searchIds(criteria(null, RegionType.PROVINCIAL)));
        assertEquals(List.of(), searchIds(criteria(Region.GIRONA, RegionType.PROVINCIAL)));
        assertEquals(0, matchRepository.countMatches(criteria(Region.LLEIDA, null)));
        assertEquals(3, searchIds(criteria(null, null)).size());
        assertNull(matchRepository.findMatchById(legacy.getId()).orElseThrow().getRegion());
    }

    @Test
    void settingTheRegionOfAnotherSourcesMatchFailsAndNothingIsWritten() {
        Match fctt = match(ImportSource.FCTT, "tdm", null, null);

        assertThrows(IllegalStateException.class, () -> backfillRepository.setRegion(
                ImportSource.BCNESA, Region.BARCELONA, RegionType.PROVINCIAL, List.of(fctt.getId())));
    }

    private MatchSearchCriteria criteria(Region region, RegionType regionType) {
        return new MatchSearchCriteria(ImportSource.BCNESA, SEASON, null, null, null, null, null, null, null, 0, 10,
                MatchStatus.SCHEDULED, region, regionType);
    }

    private List<UUID> searchIds(MatchSearchCriteria criteria) {
        return matchRepository.searchMatches(criteria).stream().map(Match::getId).sorted().toList();
    }

    private Match match(ImportSource source, String competition, Region region, RegionType regionType) {
        String suffix = UUID.randomUUID().toString();
        Team home = team("HOME " + suffix, source);
        Team away = team("AWAY " + suffix, source);
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(source)
                .competition(competition)
                .season(SEASON)
                .groupNumber(1)
                .round(1)
                .homeTeam(home)
                .awayTeam(away)
                .status(MatchStatus.SCHEDULED)
                .region(region)
                .regionType(regionType)
                .createNew();
        matchRepository.saveMatch(match);
        return match;
    }

    private Team team(String name, ImportSource source) {
        FederatedClub club = FederatedClub.createNew(source, name);
        clubRepository.saveFederatedClub(club);
        Team team = Team.createExisting(UUID.randomUUID(), source, name, SEASON, club);
        teamRepository.saveTeam(team);
        return team;
    }
}
