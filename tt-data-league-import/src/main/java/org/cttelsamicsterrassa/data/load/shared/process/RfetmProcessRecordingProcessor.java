package org.cttelsamicsterrassa.data.load.shared.process;

import org.cttelsamicsterrassa.data.load.rfetm.process.MatchContextProcessor;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;

public record RfetmProcessRecordingProcessor(MatchContextProcessor delegate,
                                              ImportProcessCollector collector)
        implements MatchContextProcessor {
    @Override
    public void process(MatchReportContext context) {
        try {
            delegate.process(context);
        } catch (RuntimeException exception) {
            collector.error(exception.getMessage(), context.matchReportFile().toString());
            throw exception;
        }
    }
}
