package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.util.UUID;

public class IllegalUnitTransitionException extends IllegalStateException {

    private final UUID unitId;
    private final UnitStatus from;
    private final UnitStatus to;

    public IllegalUnitTransitionException(UUID unitId, UnitStatus from, UnitStatus to) {
        super("Unit " + unitId + " cannot transition from " + from + " to " + to);
        this.unitId = unitId;
        this.from = from;
        this.to = to;
    }

    public UUID unitId() {
        return unitId;
    }

    public UnitStatus from() {
        return from;
    }

    public UnitStatus to() {
        return to;
    }
}
