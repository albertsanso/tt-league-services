package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.junit.jupiter.api.Test;

/** Pure unit test: needs neither Docker nor a Spring context. */
class RunScopeJsonTest {

    private final RunScopeJson json = new RunScopeJson(new ObjectMapper());

    @Test
    void writesIngestKeysOmittingNulls() {
        RunScope scope = new RunScope(List.of(new ScopeFilter("DH", null, null, "BCN", null, List.of(2, 3))));

        assertThat(json.write(scope)).isEqualTo("{\"scopes\":[{\"category\":\"DH\",\"territory\":\"BCN\",\"matchDays\":[2,3]}]}");
    }

    @Test
    void fullSeasonIsAnEmptyArray() {
        assertThat(json.write(RunScope.fullSeason())).isEqualTo("{\"scopes\":[]}");
        assertThat(json.read("{\"scopes\":[]}")).isEqualTo(RunScope.fullSeason());
    }

    @Test
    void roundTrips() {
        RunScope scope = new RunScope(List.of(
                new ScopeFilter("DH", "A", "1", "BCN", "M", List.of(1)),
                new ScopeFilter(null, null, null, null, "F", List.of())));

        assertThat(json.read(json.write(scope))).isEqualTo(scope);
    }

    @Test
    void unknownKeysFail() {
        assertThatThrownBy(() -> json.read("{\"scopes\":[{\"category\":\"A\",\"bogus\":1}]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bogus");
        assertThatThrownBy(() -> json.read("{\"scopes\":[],\"extra\":1}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedDocumentsFail() {
        assertThatThrownBy(() -> json.read("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.read("[]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.read("{\"scopes\":[{}]}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issuesRoundTrip() {
        assertThat(json.readIssues(json.writeIssues(List.of("a", "b\"c")))).containsExactly("a", "b\"c");
    }
}
