package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebPlayerImportProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

/** Registers the players of a FCTT fixture; the logic is {@link FcttWebPlayerImportProcessor}. */
@Component
@Order(FcttWebPlayerImportProcessor.ORDER)
public class FcttPlayerImportProcessor extends FcttWebPlayerImportProcessor implements FcttMatchReportProcessor {

    @Inject
    public FcttPlayerImportProcessor(PlayerSeasonRepository playerSeasonRepository) {
        super(ImportSource.FCTT, playerSeasonRepository);
    }

    @Override
    public void process(FcttMatchReportContext context) {
        super.process(context);
    }
}
