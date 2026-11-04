package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitKey;

/**
 * Identity of one ingest group without its match days, limited to the fields the source supports. The scope key
 * excludes the rounds, so back-off state survives a new round being added.
 */
public record PollUnit(String category, String group, String phase, String territory, String gender) {

    public String scopeKey() {
        return UnitKey.of(category, group, phase, territory, gender);
    }

    public ScopeFilter filter(List<Integer> matchDays) {
        return new ScopeFilter(category, group, phase, territory, gender, matchDays);
    }
}
