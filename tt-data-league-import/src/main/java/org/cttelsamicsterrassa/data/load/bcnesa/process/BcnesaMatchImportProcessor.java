package org.cttelsamicsterrassa.data.load.bcnesa.process;

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
 * Stores the matches of a BCNESA fixture; the logic is {@link FcttWebMatchImportProcessor}, which also
 * serves {@code preview(BcnesaMatchReportContext)} for the import preview.
 */
@Component
@Order(FcttWebMatchImportProcessor.ORDER)
public class BcnesaMatchImportProcessor extends FcttWebMatchImportProcessor implements BcnesaMatchReportProcessor {

    public BcnesaMatchImportProcessor(TeamRepository teamRepository,
                                      PlayerSeasonRepository playerSeasonRepository,
                                      MatchRepository matchRepository,
                                      LineupRepository lineupRepository,
                                      GameRepository gameRepository,
                                      SetScoreRepository setScoreRepository,
                                      DoublesPairRepository doublesPairRepository) {
        super(ImportSource.BCNESA, teamRepository, playerSeasonRepository, matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository);
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        super.process(context);
    }
}
