package org.cttelsamicsterrassa.data.load.shared.fcttweb.process;

import org.cttelsamicsterrassa.data.load.shared.preview.IncrementalPreviewCollector;

import java.util.Objects;

/**
 * Preview adapter (FEAT-00088): runs the read-only {@link FcttWebMatchImportProcessor#preview} for
 * each dispatched fixture and feeds the {@link IncrementalPreviewCollector}. It runs after the
 * validation processor in the same traversal and never writes. Constructed per preview run, so it is
 * a plain class, not a Spring bean.
 */
public class FcttWebPreviewClassificationProcessor {

    private final FcttWebMatchImportProcessor matchProcessor;
    private final IncrementalPreviewCollector collector;

    public FcttWebPreviewClassificationProcessor(FcttWebMatchImportProcessor matchProcessor,
                                                 IncrementalPreviewCollector collector) {
        this.matchProcessor = Objects.requireNonNull(matchProcessor, "matchProcessor");
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    public void process(FcttWebFixtureContext context) {
        collector.add(matchProcessor.preview(context));
    }
}
