package org.cttelsamicsterrassa.data.pipeline.core.alert;

/** Counts of one evaluation pass: alerts raised, cleared, sent and whose send failed. */
public record EvaluationOutcome(int raised, int cleared, int sent, int failed) {

    public boolean isEmpty() {
        return raised == 0 && cleared == 0 && sent == 0 && failed == 0;
    }
}
