package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.PollUnit;
import org.junit.jupiter.api.Test;

class UnitKeyTest {

    @Test
    void matchesThePollUnitScopeKeyByteForByte() {
        List<PollUnit> units = List.of(
                new PollUnit("SENIOR", "GROUP 1", "1a Fase", "BARCELONA", "M"),
                new PollUnit("SENIOR", null, null, null, null),
                new PollUnit(null, "G1", "FINAL", null, "F"),
                new PollUnit(null, null, null, null, null),
                new PollUnit("Div. d'Honor", "A", null, "Catalunya", null));

        for (PollUnit unit : units) {
            assertThat(UnitKey.of(unit.category(), unit.group(), unit.phase(), unit.territory(), unit.gender()))
                    .isEqualTo(unit.scopeKey());
            assertThat(UnitKey.of(unit.filter(List.of(3)))).isEqualTo(unit.scopeKey());
        }
    }

    @Test
    void isTheSha256HexOfTheCanonicalIdentity() {
        // sha256("category=A|group=|phase=|territory=|gender=")
        assertThat(UnitKey.of("A", null, null, null, null)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void excludesTheMatchDays() {
        ScopeFilter day3 = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
        ScopeFilter day4 = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(4, 5));
        ScopeFilter all = new ScopeFilter("SENIOR", "G1", null, null, null, List.of());

        assertThat(UnitKey.of(day3)).isEqualTo(UnitKey.of(day4)).isEqualTo(UnitKey.of(all));
    }

    @Test
    void distinguishesEveryIdentityField() {
        String base = UnitKey.of("A", "B", "C", "D", "E");

        assertThat(UnitKey.of("X", "B", "C", "D", "E")).isNotEqualTo(base);
        assertThat(UnitKey.of("A", "X", "C", "D", "E")).isNotEqualTo(base);
        assertThat(UnitKey.of("A", "B", "X", "D", "E")).isNotEqualTo(base);
        assertThat(UnitKey.of("A", "B", "C", "X", "E")).isNotEqualTo(base);
        assertThat(UnitKey.of("A", "B", "C", "D", "X")).isNotEqualTo(base);
    }

    @Test
    void reservedKeysAreNotHashes() {
        assertThat(UnitKey.SEASON).isEqualTo("season");
        assertThat(UnitKey.LEGACY).isEqualTo("legacy");
    }
}
