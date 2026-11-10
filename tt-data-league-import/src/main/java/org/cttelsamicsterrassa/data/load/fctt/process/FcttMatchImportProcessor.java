package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebMatchImportProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stores the matches of a FCTT fixture; the logic is {@link FcttWebMatchImportProcessor}, which also
 * serves {@code preview(FcttMatchReportContext)} for the import preview.
 */
@Component
@Order(FcttWebMatchImportProcessor.ORDER)
public class FcttMatchImportProcessor extends FcttWebMatchImportProcessor implements FcttMatchReportProcessor {

    public FcttMatchImportProcessor(TeamRepository teamRepository,
                                      PlayerSeasonRepository playerSeasonRepository,
                                      MatchRepository matchRepository,
                                      LineupRepository lineupRepository,
                                      GameRepository gameRepository,
                                      SetScoreRepository setScoreRepository,
                                      DoublesPairRepository doublesPairRepository) {
        super(ImportSource.FCTT, teamRepository, playerSeasonRepository, matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository);
    }

    @Override
    public void process(FcttMatchReportContext context) {
        super.process(context);
    }
}
