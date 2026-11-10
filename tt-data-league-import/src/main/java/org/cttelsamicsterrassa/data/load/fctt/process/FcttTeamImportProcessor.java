package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebTeamImportProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

/** Registers the clubs of a FCTT fixture; the logic is {@link FcttWebTeamImportProcessor}. */
@Component
@Order(FcttWebTeamImportProcessor.ORDER)
public class FcttTeamImportProcessor extends FcttWebTeamImportProcessor implements FcttMatchReportProcessor {

    @Inject
    public FcttTeamImportProcessor(TeamRepository teamRepository) {
        super(ImportSource.FCTT, teamRepository);
    }

    @Override
    public void process(FcttMatchReportContext context) {
        super.process(context);
    }
}
