package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnitPlannerTest {

    private static final UUID RUN = UUID.randomUUID();

    private static ScopeFilter filter(String category, String group, String phase, String territory, String gender,
            Integer... matchDays) {
        return new ScopeFilter(category, group, phase, territory, gender, List.of(matchDays));
    }

    @Test
    void aFullSeasonScopeIsOneSeasonUnit() {
        List<RunUnit> units = UnitPlanner.plan(RUN, RunScope.fullSeason());

        assertThat(units).hasSize(1);
        RunUnit unit = units.get(0);
        assertThat(unit.unitKey()).isEqualTo(UnitKey.SEASON);
        assertThat(unit.label()).isEqualTo("Full season");
        assertThat(unit.ordinal()).isZero();
        assertThat(unit.runId()).isEqualTo(RUN);
        assertThat(unit.status()).isEqualTo(UnitStatus.PENDING);
        assertThat(unit.scope().isFullSeason()).isTrue();
    }

    @Test
    void oneFilterIsOneUnitKeyedByItsIdentity() {
        ScopeFilter filter = filter("SENIOR", "G1", null, null, null, 3);

        List<RunUnit> units = UnitPlanner.plan(RUN, new RunScope(List.of(filter)));

        assertThat(units).hasSize(1);
        assertThat(units.get(0).unitKey()).isEqualTo(UnitKey.of(filter));
        assertThat(units.get(0).scope().filters()).containsExactly(filter);
    }

    @Test
    void oneUnitPerDistinctIdentityInFirstSeenOrder() {
        ScopeFilter b = filter("SENIOR", "G2", null, null, null, 1);
        ScopeFilter a = filter("SENIOR", "G1", null, null, null, 1);
        ScopeFilter c = filter("JUNIOR", "G1", null, null, null, 2);

        List<RunUnit> units = UnitPlanner.plan(RUN, new RunScope(List.of(b, a, c)));

        assertThat(units).extracting(RunUnit::ordinal).containsExactly(0, 1, 2);
        assertThat(units).extracting(RunUnit::unitKey)
                .containsExactly(UnitKey.of(b), UnitKey.of(a), UnitKey.of(c));
        assertThat(units).extracting(RunUnit::id).doesNotHaveDuplicates();
    }

    @Test
    void filtersSharingAnIdentityMergeTheirMatchDays() {
        ScopeFilter day4 = filter("SENIOR", "G1", null, null, null, 4);
        ScopeFilter days32 = filter("SENIOR", "G1", null, null, null, 3, 2);
        ScopeFilter other = filter("SENIOR", "G2", null, null, null, 1);

        List<RunUnit> units = UnitPlanner.plan(RUN, new RunScope(List.of(day4, other, days32)));

        assertThat(units).hasSize(2);
        assertThat(units.get(0).scope().filters().get(0).matchDays()).containsExactly(2, 3, 4);
        assertThat(units.get(1).scope().filters().get(0).matchDays()).containsExactly(1);
    }

    @Test
    void aFilterWithoutMatchDaysMeansAllOfThemAndWinsTheMerge() {
        ScopeFilter all = filter("SENIOR", "G1", null, null, null);
        ScopeFilter day3 = filter("SENIOR", "G1", null, null, null, 3);

        List<RunUnit> units = UnitPlanner.plan(RUN, new RunScope(List.of(day3, all)));

        assertThat(units).hasSize(1);
        assertThat(units.get(0).scope().filters().get(0).matchDays()).isEmpty();
    }

    @Test
    void labelsListTheSetFieldsThenTheMatchDays() {
        assertThat(UnitPlanner.label(filter("SENIOR", "G1", "1a Fase", "BARCELONA", "M", 3, 4)))
                .isEqualTo("BARCELONA SENIOR M G1 1a Fase · J3,J4");
        assertThat(UnitPlanner.label(filter("SENIOR", null, null, null, null))).isEqualTo("SENIOR");
        assertThat(UnitPlanner.label(filter(null, null, null, null, null, 2))).isEqualTo("J2");
        assertThat(UnitPlanner.label(filter(null, "G1", null, null, null, 7))).isEqualTo("G1 · J7");
    }

    @Test
    void aVeryLongLabelIsTruncatedToTheLimit() {
        String label = UnitPlanner.label(filter("x".repeat(300), null, null, null, null));

        assertThat(label).hasSize(RunUnit.MAX_LABEL).endsWith("…");
    }

    @Test
    void replanCopiesTheGivenUnitsAsNewPendingOnesKeepingOrdinalKeyLabelAndScope() {
        UUID newRun = UUID.randomUUID();
        List<RunUnit> originals = UnitPlanner.plan(RUN, new RunScope(List.of(
                filter("SENIOR", "G1", null, null, null, 1), filter("SENIOR", "G2", null, null, null, 2))));
        RunUnit failed = originals.get(1).fail(new RunError("X", "x"), java.time.Instant.parse("2026-10-04T10:00:00Z"));

        List<RunUnit> copies = UnitPlanner.replan(newRun, List.of(failed));

        assertThat(copies).hasSize(1);
        RunUnit copy = copies.get(0);
        assertThat(copy.runId()).isEqualTo(newRun);
        assertThat(copy.id()).isNotEqualTo(failed.id());
        assertThat(copy.ordinal()).isEqualTo(1);
        assertThat(copy.unitKey()).isEqualTo(failed.unitKey());
        assertThat(copy.label()).isEqualTo(failed.label());
        assertThat(copy.scope()).isEqualTo(failed.scope());
        assertThat(copy.status()).isEqualTo(UnitStatus.PENDING);
        assertThat(copy.error()).isNull();
    }
}
