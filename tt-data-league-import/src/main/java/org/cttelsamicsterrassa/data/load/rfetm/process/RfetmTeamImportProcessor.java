package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.club.CanonicalClubResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores the two clubs of a match report, and their entry for that season.
 *
 * <p>Clubs are resolved by {@link RfetmClubKey}: the RFETM team id where the payload carries one, and
 * the team name scoped to season and competition where it does not. A bare name is never the key -
 * names drift across seasons and one name is normally shared by the A, B and C teams of a club, so an
 * unscoped name upsert would merge teams that must stay apart. Scoping to the competition is what
 * keeps them distinct; see {@link RfetmClubKey} for the measurement behind that choice.</p>
 *
 * <p>A club keeps the name it was first seen under; the name as written in a given season is
 * recorded on that season's {@code TEAM} row instead.</p>
 */
@Component
@Order(RfetmTeamImportProcessor.ORDER)
public class RfetmTeamImportProcessor implements MatchContextProcessor {

    /** Clubs come first: players and matches both reference them. */
    public static final int ORDER = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmTeamImportProcessor.class);

    private final TeamRepository teamRepository;
    private final FederatedClubRepository federatedClubRepository;
    private final CanonicalClubResolver canonicalClubResolver;
    private final Map<String, FederatedClub> federatedClubsByKey = new LinkedHashMap<>();

    @Inject
    public RfetmTeamImportProcessor(TeamRepository teamRepository,
                                    FederatedClubRepository federatedClubRepository,
                                    ClubRepository clubRepository) {
        this.teamRepository = teamRepository;
        this.federatedClubRepository = federatedClubRepository;
        this.canonicalClubResolver = new CanonicalClubResolver(clubRepository);
    }

    /**
     * Compatibility constructor for clients that only need the historical team behavior.
     */
    public RfetmTeamImportProcessor(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
        this.federatedClubRepository = null;
        this.canonicalClubResolver = null;
    }

    @Override
    public void process(MatchReportContext context) {
        Season season = context.toSeason();
        importClub(context.homeTeam(), homeTeam(context), season);
        importClub(context.awayTeam(), awayTeam(context), season);
    }

    private void importClub(RfetmClubKey key, ActaTeam team, Season season) {
        String name = team != null ? team.name() : key.name();

        teamRepository.findTeamByNameAndSeasonAndSource(name, season, ImportSource.RFETM)
                .ifPresentOrElse(existing -> updateFederatedClub(existing, key, name),
                        () -> {
                    FederatedClub federatedClub = resolveFederatedClub(key, name);
                    Team created = Team.createNew(ImportSource.RFETM, name, season, federatedClub);
                    teamRepository.saveTeam(created);
                    LOGGER.debug("Created team {} {} ({})", name, season, key);
                });
    }

    private void updateFederatedClub(Team existing, RfetmClubKey key, String name) {
        if (federatedClubRepository == null) {
            return;
        }
        FederatedClub federatedClub = existing.getFederatedClub()
                .map(existingClub -> {
                    FederatedClub knownClub = key == null ? null : federatedClubsByKey.get(key.value());
                    if (knownClub != null && !knownClub.getId().equals(existingClub.getId())) {
                        return knownClub;
                    }
                    if (key != null) {
                        federatedClubsByKey.putIfAbsent(key.value(), existingClub);
                    }
                    if (existingClub.getClub().isPresent()) {
                        return existingClub;
                    }
                    Club canonicalClub = canonicalClubResolver.resolveOrCreate(name);
                    FederatedClub linked = existingClub.withClub(canonicalClub);
                    federatedClubRepository.saveFederatedClub(linked);
                    if (key != null) {
                        federatedClubsByKey.put(key.value(), linked);
                    }
                    return linked;
                })
                .orElseGet(() -> resolveFederatedClub(key, name));
        if (existing.getFederatedClub().map(club -> sameFederatedClub(club, federatedClub)).orElse(false)) {
            return;
        }
        teamRepository.saveTeam(existing.withFederatedClub(federatedClub));
    }

    private static boolean sameFederatedClub(FederatedClub left, FederatedClub right) {
        return left.getId().equals(right.getId())
                && left.getClub().map(a -> right.getClub().map(b -> a.getId().equals(b.getId())).orElse(false))
                .orElse(right.getClub().isEmpty());
    }

    private FederatedClub resolveFederatedClub(RfetmClubKey key, String name) {
        if (federatedClubRepository == null) {
            return null;
        }
        if (key != null) {
            FederatedClub known = federatedClubsByKey.get(key.value());
            if (known != null) {
                return known;
            }
        }
        Club canonicalClub = canonicalClubResolver.resolveOrCreate(name);
        FederatedClub resolved = federatedClubRepository.findFederatedClubBySourceAndName(ImportSource.RFETM, name)
                .map(existing -> {
                    if (existing.getClub().isPresent()) {
                        return existing;
                    }
                    FederatedClub linked = existing.withClub(canonicalClub);
                    federatedClubRepository.saveFederatedClub(linked);
                    return linked;
                })
                .orElseGet(() -> {
                    FederatedClub created = FederatedClub.createNew(ImportSource.RFETM, name, canonicalClub);
                    federatedClubRepository.saveFederatedClub(created);
                    return created;
                });
        if (key != null) {
            federatedClubsByKey.put(key.value(), resolved);
        }
        return resolved;
    }

    private static ActaTeam homeTeam(MatchReportContext context) {
        return context.acta() == null || context.acta().teams() == null ? null : context.acta().teams().home();
    }

    private static ActaTeam awayTeam(MatchReportContext context) {
        return context.acta() == null || context.acta().teams() == null ? null : context.acta().teams().away();
    }
}
