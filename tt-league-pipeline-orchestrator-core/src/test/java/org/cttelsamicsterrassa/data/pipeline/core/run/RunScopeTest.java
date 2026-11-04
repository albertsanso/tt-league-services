package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RunScopeTest {

    @Test
    void emptyFilterIsRejected() {
        assertThatThrownBy(() -> new ScopeFilter(null, null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScopeFilter(null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankFieldAndNonPositiveMatchDayAreRejected() {
        assertThatThrownBy(() -> new ScopeFilter(" ", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScopeFilter(null, null, null, null, null, List.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchDaysOnlyFilterIsValid() {
        assertThat(new ScopeFilter(null, null, null, null, null, List.of(3, 4)).matchDays()).containsExactly(3, 4);
    }

    @Test
    void deduplicatesKeepingFirstSeenOrder() {
        ScopeFilter a = new ScopeFilter("A", null, null, null, null, null);
        ScopeFilter b = new ScopeFilter("B", null, null, null, "M", List.of(1));
        assertThat(new RunScope(List.of(b, a, b, a)).filters()).containsExactly(b, a);
    }

    @Test
    void fullSeasonHasNoFilters() {
        assertThat(RunScope.fullSeason().filters()).isEmpty();
        assertThat(RunScope.fullSeason().isFullSeason()).isTrue();
    }
}
