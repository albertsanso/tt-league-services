package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.preview.ImportPreviewCollector;

/** Preview validation of a FCTT fixture; the logic is {@link FcttWebPreviewValidationProcessor}. */
public class FcttPreviewValidationProcessor extends FcttWebPreviewValidationProcessor
        implements FcttMatchReportProcessor {

    public FcttPreviewValidationProcessor(ImportPreviewCollector collector) {
        super(ImportSource.FCTT, collector);
    }

    @Override
    public void process(FcttMatchReportContext context) {
        super.process(context);
    }
}
