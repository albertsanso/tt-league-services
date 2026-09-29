package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.load.shared.preview.IncrementalPreviewCollector;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;

import java.util.Objects;

/**
 * Preview adapter (FEAT-00088): runs the read-only {@link RfetmMatchImportProcessor#preview} for each
 * dispatched RFETM report and feeds the {@link IncrementalPreviewCollector}. It runs after the
 * validation processor in the same traversal and never writes. Constructed per preview run, so it is
 * a plain class, not a Spring bean.
 */
public class RfetmPreviewClassificationProcessor implements MatchContextProcessor {

    private final RfetmMatchImportProcessor matchProcessor;
    private final IncrementalPreviewCollector collector;

    public RfetmPreviewClassificationProcessor(RfetmMatchImportProcessor matchProcessor,
                                               IncrementalPreviewCollector collector) {
        this.matchProcessor = Objects.requireNonNull(matchProcessor, "matchProcessor");
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    @Override
    public void process(MatchReportContext context) {
        collector.add(matchProcessor.preview(context));
    }
}
