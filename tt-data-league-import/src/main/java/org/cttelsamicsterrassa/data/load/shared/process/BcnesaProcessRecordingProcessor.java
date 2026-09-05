package org.cttelsamicsterrassa.data.load.shared.process;

import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;

public record BcnesaProcessRecordingProcessor(BcnesaMatchReportProcessor delegate,
                                               ImportProcessCollector collector)
        implements BcnesaMatchReportProcessor {
    @Override
    public void process(BcnesaMatchReportContext context) {
        try {
            delegate.process(context);
        } catch (RuntimeException exception) {
            collector.error(exception.getMessage(), context.matchReportFile().toString());
            throw exception;
        }
    }
}
