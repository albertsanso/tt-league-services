package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebPlayerImportProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

/** Registers the players of a BCNESA fixture; the logic is {@link FcttWebPlayerImportProcessor}. */
@Component
@Order(FcttWebPlayerImportProcessor.ORDER)
public class BcnesaPlayerImportProcessor extends FcttWebPlayerImportProcessor implements BcnesaMatchReportProcessor {

    @Inject
    public BcnesaPlayerImportProcessor(PlayerSeasonRepository playerSeasonRepository) {
        super(ImportSource.BCNESA, playerSeasonRepository);
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        super.process(context);
    }
}
