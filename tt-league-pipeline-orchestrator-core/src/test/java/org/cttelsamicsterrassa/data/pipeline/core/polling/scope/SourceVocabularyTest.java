package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.junit.jupiter.api.Test;

class SourceVocabularyTest {

    private static final String SEASON = "2026-2027";

    private final SourceVocabulary rfetm = SourceVocabulary.of(PipelineSource.RFETM, BcnesaCompetitionNames.defaults());
    private final SourceVocabulary fctt = SourceVocabulary.of(PipelineSource.FCTT, BcnesaCompetitionNames.defaults());
    private final SourceVocabulary bcnesa =
            SourceVocabulary.of(PipelineSource.BCNESA, BcnesaCompetitionNames.defaults());

    static IngestStatusRow row(
            String category, String group, String phase, String gender, String territory, int matchDay) {
        return new IngestStatusRow(SEASON, category, group, phase, gender, territory, matchDay, "scheduled", null,
                null);
    }

    private static MatchDayKey key(
            PipelineSource source, String competition, Integer group, String phase, int round) {
        return new MatchDayKey(source, SEASON, competition, group, phase, round);
    }

    @Test
    void rfetmKeysAreCategoryAndSexWithTheGroupNumber() {
        IngestStatusRow row = row("super-divisio", "2", null, "masculino", null, 5);

        assertThat(rfetm.platformKey(row)).contains(new PlatformGroupKey("super-divisio-masculino", 2, null));
        assertThat(rfetm.matches(row, key(PipelineSource.RFETM, "super-divisio-masculino", 2, null, 5))).isTrue();
        assertThat(rfetm.matches(row, key(PipelineSource.RFETM, "super-divisio-masculino", 2, null, 6))).isFalse();
        assertThat(rfetm.matches(row, key(PipelineSource.RFETM, "super-divisio-femenino", 2, null, 5))).isFalse();
        assertThat(rfetm.matches(row, key(PipelineSource.RFETM, "super-divisio-masculino", 3, null, 5))).isFalse();
    }

    @Test
    void rfetmUnitsAreOnePerCategoryBecauseScopesSupportOnlyTheCategory() {
        PollUnit male = rfetm.unit(row("super-divisio", "2", null, "masculino", null, 5));
        PollUnit female = rfetm.unit(row("super-divisio", "1", null, "femenino", null, 3));

        assertThat(male).isEqualTo(new PollUnit("super-divisio", null, null, null, null)).isEqualTo(female);
    }

    @Test
    void rfetmRowsWithoutAGroupNumberOrSexCannotBeMapped() {
        assertThat(rfetm.platformKey(row("super-divisio", "abc", null, "masculino", null, 1))).isEmpty();
        assertThat(rfetm.platformKey(row("super-divisio", null, null, "masculino", null, 1))).isEmpty();
        assertThat(rfetm.platformKey(row("super-divisio", "1", null, null, null, 1))).isEmpty();
        assertThat(rfetm.platformKey(row(null, "1", null, "masculino", null, 1))).isEmpty();
    }

    @Test
    void fcttMapsMaleAndFemaleToTheRfetmSexVocabulary() {
        assertThat(fctt.platformKey(row("tercera", "G1", "1a Fase", "male", "Barcelona", 2)))
                .contains(new PlatformGroupKey("tercera-masculino", 1, null));
        assertThat(fctt.platformKey(row("tercera", "G12", "1a Fase", "female", null, 2)))
                .contains(new PlatformGroupKey("tercera-femenino", 12, null));
        assertThat(fctt.platformKey(row("tercera", "3", null, "male", null, 2)))
                .contains(new PlatformGroupKey("tercera-masculino", 3, null));
    }

    @Test
    void fcttRowsWithAnUnknownGenderOrGroupCannotBeMapped() {
        assertThat(fctt.platformKey(row("tercera", "G1", null, "mixed", null, 2))).isEmpty();
        assertThat(fctt.platformKey(row("tercera", "G1", null, null, null, 2))).isEmpty();
        assertThat(fctt.platformKey(row("tercera", "Final", null, "male", null, 2))).isEmpty();
    }

    @Test
    void fcttJoinsEveryPhaseOfTheGroupAndUnitsKeepAllSixFields() {
        IngestStatusRow row = row("tercera", "G1", "2a Fase", "male", "Barcelona", 2);

        assertThat(fctt.matches(row, key(PipelineSource.FCTT, "tercera-masculino", 1, "1a Fase", 2))).isTrue();
        assertThat(fctt.matches(row, key(PipelineSource.FCTT, "tercera-masculino", 1, null, 2))).isTrue();
        assertThat(fctt.unit(row)).isEqualTo(new PollUnit("tercera", "G1", "2a Fase", "Barcelona", "male"));
    }

    @Test
    void bcnesaMapsRtbFoldersToTheLegacyNames() {
        assertThat(bcnesa.platformKey(row("rtb-segona-a", "G1", "Fase 1", null, "Barcelona", 3)))
                .contains(new PlatformGroupKey("Segona _A_", 1, "Fase 1"));
        assertThat(bcnesa.platformKey(row("RTB-Veterans-4a-C", "G2", "Unica", null, null, 3)))
                .contains(new PlatformGroupKey("Vet 4a _C_", 2, "Unica"));
    }

    @Test
    void bcnesaStoresOtherFoldersUnchanged() {
        assertThat(bcnesa.platformKey(row("Preferent", "G1", "Fase 1", null, null, 1)))
                .contains(new PlatformGroupKey("Preferent", 1, "Fase 1"));
    }

    @Test
    void bcnesaUnmappedRtbFolderCannotBeMapped() {
        assertThat(bcnesa.platformKey(row("rtb-unknown", "G1", "Fase 1", null, null, 1))).isEmpty();
    }

    @Test
    void bcnesaVeteransOtherGroupHasNoGroupNumber() {
        IngestStatusRow row = row("rtb-veterans-1a", "Other", "Unica", null, null, 4);

        assertThat(bcnesa.platformKey(row)).contains(new PlatformGroupKey("Vet 1a", null, "Unica"));
        assertThat(bcnesa.matches(row, key(PipelineSource.BCNESA, "Vet 1a", null, "Unica", 4))).isTrue();
    }

    @Test
    void bcnesaGroupMustBeGPlusNumber() {
        assertThat(bcnesa.platformKey(row("rtb-primera", "1", "Fase 1", null, null, 1))).isEmpty();
        assertThat(bcnesa.platformKey(row("rtb-primera", "Groupe", "Fase 1", null, null, 1))).isEmpty();
        assertThat(bcnesa.platformKey(row("rtb-primera", null, "Fase 1", null, null, 1))).isEmpty();
    }

    @Test
    void bcnesaComparesThePhase() {
        IngestStatusRow row = row("rtb-primera", "G1", "Fase 2", null, null, 1);

        assertThat(bcnesa.matches(row, key(PipelineSource.BCNESA, "Primera", 1, "Fase 2", 1))).isTrue();
        assertThat(bcnesa.matches(row, key(PipelineSource.BCNESA, "Primera", 1, "Fase 1", 1))).isFalse();
        assertThat(bcnesa.unit(row)).isEqualTo(new PollUnit("rtb-primera", "G1", "Fase 2", null, null));
    }

    @Test
    void bcnesaNamesComeFromConfigurationAndAreValidated() {
        BcnesaCompetitionNames custom = new BcnesaCompetitionNames(Map.of("RTB-Nova", "Nova"));

        assertThat(custom.storedName("rtb-nova")).contains("Nova");
        assertThat(custom.storedName("rtb-primera")).isEmpty();
        assertThat(BcnesaCompetitionNames.defaultEntries()).hasSize(16);
        assertThatThrownBy(() -> new BcnesaCompetitionNames(Map.of(" ", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopeKeysAreStableHexDigestsThatIgnoreMatchDays() {
        PollUnit unit = new PollUnit("tercera", "G1", null, null, "male");

        assertThat(unit.scopeKey()).hasSize(64).matches("[0-9a-f]+")
                .isEqualTo(new PollUnit("tercera", "G1", null, null, "male").scopeKey())
                .isNotEqualTo(new PollUnit("tercera", "G2", null, null, "male").scopeKey());
        assertThat(unit.filter(java.util.List.of(1, 2)).matchDays()).containsExactly(1, 2);
    }
}
