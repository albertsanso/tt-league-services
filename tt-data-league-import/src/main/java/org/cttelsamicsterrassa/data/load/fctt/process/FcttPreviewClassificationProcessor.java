package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.load.shared.preview.IncrementalPreviewCollector;

import java.util.Objects;

/**
 * Preview adapter (FEAT-00088): runs the read-only {@link FcttMatchImportProcessor#preview} for each
 * dispatched FCTT report and feeds the {@link IncrementalPreviewCollector}. It runs after the
 * validation processor in the same traversal and never writes. Constructed per preview run, so it is
 * a plain class, not a Spring bean.
 */
public class FcttPreviewClassificationProcessor implements FcttMatchReportProcessor {

    private final FcttMatchImportProcessor matchProcessor;
    private final IncrementalPreviewCollector collector;

    public FcttPreviewClassificationProcessor(FcttMatchImportProcessor matchProcessor,
                                              IncrementalPreviewCollector collector) {
        this.matchProcessor = Objects.requireNonNull(matchProcessor, "matchProcessor");
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    @Override
    public void process(FcttMatchReportContext context) {
        collector.add(matchProcessor.preview(context));
    }
}
