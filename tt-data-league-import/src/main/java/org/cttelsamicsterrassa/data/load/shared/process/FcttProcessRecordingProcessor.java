package org.cttelsamicsterrassa.data.load.shared.process;

import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;

public record FcttProcessRecordingProcessor(FcttMatchReportProcessor delegate,
                                             ImportProcessCollector collector)
        implements FcttMatchReportProcessor {
    @Override
    public void process(FcttMatchReportContext context) {
        try {
            delegate.process(context);
        } catch (RuntimeException exception) {
            collector.error(exception.getMessage(), context.matchReportFile().toString());
            throw exception;
        }
    }
}
