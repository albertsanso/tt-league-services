package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import org.cttelsamicsterrassa.data.load.shared.club.CanonicalClubResolver;

/**
 * Stores the FCTT report's two clubs and their season entries.
 *
 * <p>FCTT club lookup is explicitly scoped to {@link ImportSource#FCTT}. Team ids in the shared
 * payload are RFETM-shaped fields and therefore are not used as FCTT club identity.</p>
 */
@Component
@Order(FcttTeamImportProcessor.ORDER)
public class FcttTeamImportProcessor implements FcttMatchReportProcessor {

    /** Clubs must exist before player and match processors run. */
    public static final int ORDER = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttTeamImportProcessor.class);

    private final TeamRepository teamRepository;
    private final FederatedClubRepository federatedClubRepository;
    private final CanonicalClubResolver canonicalClubResolver;

    @Inject
    public FcttTeamImportProcessor(TeamRepository teamRepository,
                                   FederatedClubRepository federatedClubRepository,
                                   ClubRepository clubRepository) {
        this.teamRepository = teamRepository;
        this.federatedClubRepository = federatedClubRepository;
        this.canonicalClubResolver = new CanonicalClubResolver(clubRepository);
    }

    public FcttTeamImportProcessor(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
        this.federatedClubRepository = null;
        this.canonicalClubResolver = null;
    }

    @Override
    public void process(FcttMatchReportContext context) {
        if (context.acta().teams() == null) {
            LOGGER.warn("No teams in {}; no clubs imported", context.matchReportFile());
            return;
        }

        Season season = context.toSeason();
        importClub(context.acta().teams().home(), season, context);
        importClub(context.acta().teams().away(), season, context);
    }

    private void importClub(ActaTeam team, Season season, FcttMatchReportContext context) {
        if (team == null || isBlank(team.name())) {
            LOGGER.warn("Skipping FCTT team without a name in {}", context.matchReportFile());
            return;
        }
        teamRepository.findTeamByNameAndSeasonAndSource(team.name(), season, ImportSource.FCTT)
                .ifPresentOrElse(existing -> updateFederatedClub(existing, team.name()),
                        () -> {
                    FederatedClub federatedClub = resolveFederatedClub(team.name());
                    Team created = Team.createNew(ImportSource.FCTT, team.name(), season, federatedClub);
                    teamRepository.saveTeam(created);
                    LOGGER.debug("Created FCTT team {} {}", team.name(), season);
                });
    }

    private void updateFederatedClub(Team existing, String name) {
        if (federatedClubRepository == null) {
            return;
        }
        FederatedClub federatedClub = resolveFederatedClub(name);
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

    private FederatedClub resolveFederatedClub(String name) {
        if (federatedClubRepository == null) {
            return null;
        }
        Club canonicalClub = canonicalClubResolver.resolveOrCreate(name);
        return federatedClubRepository.findFederatedClubBySourceAndName(ImportSource.FCTT, name)
                .map(existing -> {
                    if (existing.getClub().isPresent()) {
                        return existing;
                    }
                    FederatedClub linked = existing.withClub(canonicalClub);
                    federatedClubRepository.saveFederatedClub(linked);
                    return linked;
                })
                .orElseGet(() -> {
                    FederatedClub created = FederatedClub.createNew(ImportSource.FCTT, name, canonicalClub);
                    federatedClubRepository.saveFederatedClub(created);
                    return created;
                });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
