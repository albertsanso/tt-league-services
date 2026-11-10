package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.preview.ImportPreviewCollector;

/** Preview validation of a BCNESA fixture; the logic is {@link FcttWebPreviewValidationProcessor}. */
public class BcnesaPreviewValidationProcessor extends FcttWebPreviewValidationProcessor
        implements BcnesaMatchReportProcessor {

    public BcnesaPreviewValidationProcessor(ImportPreviewCollector collector) {
        super(ImportSource.BCNESA, collector);
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        super.process(context);
    }
}
