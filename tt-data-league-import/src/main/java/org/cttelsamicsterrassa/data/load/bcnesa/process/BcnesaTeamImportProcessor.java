package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebTeamImportProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

/** Registers the clubs of a BCNESA fixture; the logic is {@link FcttWebTeamImportProcessor}. */
@Component
@Order(FcttWebTeamImportProcessor.ORDER)
public class BcnesaTeamImportProcessor extends FcttWebTeamImportProcessor implements BcnesaMatchReportProcessor {

    @Inject
    public BcnesaTeamImportProcessor(TeamRepository teamRepository) {
        super(ImportSource.BCNESA, teamRepository);
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        super.process(context);
    }
}
