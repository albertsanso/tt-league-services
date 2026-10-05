package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.List;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;

/** The disjoint poll units of a source's open match days. */
public record ScopeBuild(List<PollUnitScope> units) {

    public ScopeBuild {
        units = List.copyOf(Objects.requireNonNull(units, "units is required"));
    }

    /** The scope of every unit; the caller must handle an empty build, which has no filter. */
    public RunScope runScope() {
        return new RunScope(units.stream().map(PollUnitScope::filter).toList());
    }
}
