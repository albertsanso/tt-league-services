package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import org.cttelsamicsterrassa.data.load.shared.club.CanonicalClubResolver;

/**
 * Stores the two clubs of a BCNESA fixture, and their entry for that season.
 *
 * <p>BCNESA carries no team id ({@code equipos.*.id} is null throughout the export), so clubs resolve
 * by {@link BcnesaTeamNames#normalize(String) normalized name} scoped to
 * {@link ImportSource#BCNESA}, never to name alone: a handful of names coincide with RFETM clubs by
 * chance without being the same entity, and the two federations must never merge on that basis.</p>
 */
@Component
@Order(BcnesaTeamImportProcessor.ORDER)
public class BcnesaTeamImportProcessor implements BcnesaMatchReportProcessor {

    /** Clubs come first: players and matches both reference them. */
    public static final int ORDER = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(BcnesaTeamImportProcessor.class);

    private final TeamRepository teamRepository;
    private final FederatedClubRepository federatedClubRepository;
    private final CanonicalClubResolver canonicalClubResolver;

    @Inject
    public BcnesaTeamImportProcessor(TeamRepository teamRepository,
                                     FederatedClubRepository federatedClubRepository,
                                     ClubRepository clubRepository) {
        this.teamRepository = teamRepository;
        this.federatedClubRepository = federatedClubRepository;
        this.canonicalClubResolver = new CanonicalClubResolver(clubRepository);
    }

    public BcnesaTeamImportProcessor(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
        this.federatedClubRepository = null;
        this.canonicalClubResolver = null;
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        Season season = context.toSeason();
        importClub(context.homeTeamName(), season);
        importClub(context.awayTeamName(), season);
    }

    private void importClub(String rawName, Season season) {
        String name = BcnesaTeamNames.normalize(rawName);
        if (name == null) {
            return;
        }

        teamRepository.findTeamByNameAndSeasonAndSource(name, season, ImportSource.BCNESA)
                .ifPresentOrElse(existing -> updateFederatedClub(existing, name),
                        () -> {
                    FederatedClub federatedClub = resolveFederatedClub(name);
                    Team created = Team.createNew(ImportSource.BCNESA, name, season, federatedClub);
                    teamRepository.saveTeam(created);
                    LOGGER.debug("Created BCNESA team {} {}", name, season);
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
        return federatedClubRepository.findFederatedClubBySourceAndName(ImportSource.BCNESA, name)
                .map(existing -> {
                    if (existing.getClub().isPresent()) {
                        return existing;
                    }
                    FederatedClub linked = existing.withClub(canonicalClub);
                    federatedClubRepository.saveFederatedClub(linked);
                    return linked;
                })
                .orElseGet(() -> {
                    FederatedClub created = FederatedClub.createNew(ImportSource.BCNESA, name, canonicalClub);
                    federatedClubRepository.saveFederatedClub(created);
                    return created;
                });
    }
}
