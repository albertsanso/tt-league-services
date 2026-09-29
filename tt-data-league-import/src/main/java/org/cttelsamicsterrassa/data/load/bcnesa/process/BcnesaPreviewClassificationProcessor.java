package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.load.shared.preview.IncrementalPreviewCollector;

import java.util.Objects;

/**
 * Preview adapter (FEAT-00088): runs the read-only {@link BcnesaMatchImportProcessor#preview} for
 * each dispatched BCNESA fixture and feeds the {@link IncrementalPreviewCollector}. It runs after the
 * validation processor in the same traversal and never writes. Constructed per preview run, so it is
 * a plain class, not a Spring bean.
 */
public class BcnesaPreviewClassificationProcessor implements BcnesaMatchReportProcessor {

    private final BcnesaMatchImportProcessor matchProcessor;
    private final IncrementalPreviewCollector collector;

    public BcnesaPreviewClassificationProcessor(BcnesaMatchImportProcessor matchProcessor,
                                                IncrementalPreviewCollector collector) {
        this.matchProcessor = Objects.requireNonNull(matchProcessor, "matchProcessor");
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        collector.add(matchProcessor.preview(context));
    }
}
