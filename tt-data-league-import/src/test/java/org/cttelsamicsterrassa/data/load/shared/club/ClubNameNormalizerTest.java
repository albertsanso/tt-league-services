package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubNameNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClubNameNormalizerTest {

    private final ClubNameNormalizer normalizer = new ClubNameNormalizer();

    @Test
    void foldsCaseWhitespacePunctuationAndVerifiedAbbreviations() {
        assertEquals(
                "hortitec alzira",
                normalizer.exactKey(ImportSource.FCTT, "  HORTITEC,   ALZIRA   T.T. "));
        assertEquals(
                "hortitec alzira",
                normalizer.exactKey(ImportSource.FCTT, "HORTITEC ALZIRA TT"));
    }

    @Test
    void preservesAccentedTokensAsTheSameLetters() {
        assertEquals(
                "cer robin hat prisco lescala",
                normalizer.exactKey(ImportSource.FCTT, "C.E.R. ROBIN HAT PRISCO L´ESCALA"));
        assertEquals(
                normalizer.exactKey(ImportSource.FCTT, "CER ROBIN HAT PRISCO LESCALA"),
                normalizer.exactKey(ImportSource.FCTT, "C.E.R. ROBIN HAT PRISCO L´ESCALA"));
    }

    @Test
    void keepsCttAsABcnesaIdentityToken() {
        assertEquals("ctt ateneu", normalizer.exactKey(ImportSource.BCNESA, "CTT ATENEU"));
        assertNotEquals(
                normalizer.exactKey(ImportSource.BCNESA, "CTT ATENEU"),
                normalizer.exactKey(ImportSource.BCNESA, "CTT DELS HORTS"));
    }

    @Test
    void removesOnlyRecognizedBcnesaTeamAndCategoryQualifiers() {
        assertEquals("cc sant andreu", normalizer.exactKey(ImportSource.BCNESA, "CC SANT ANDREU A Vet"));
        assertEquals("ctt sant quirze del valles",
                normalizer.exactKey(ImportSource.BCNESA, "CTT SANT QUIRZE DEL VALLÈS - Sen A"));
        assertEquals("ctt sant quirze del valles",
                normalizer.exactKey(ImportSource.BCNESA, "CTT SANT QUIRZE DEL VALLÈS - Vet C"));
        assertEquals("tt sant andreu",
                normalizer.exactKey(ImportSource.BCNESA, "TT SANT ANDREU -A-"));
        assertEquals("tt sant andreu",
                normalizer.exactKey(ImportSource.BCNESA, "TT SANT ANDREU -B-"));
        assertEquals("ctt dels horts 2000", normalizer.exactKey(ImportSource.BCNESA, "CTT DELS HORTS 2000 B"));
        assertEquals("ctt dels horts 2000", normalizer.exactKey(ImportSource.BCNESA, "CTT DELS HORTS 2000"));
    }

    @Test
    void expandsOnlyTheApprovedSpellingAndAffixRules() {
        assertEquals("cett sant andreu la barca",
                normalizer.exactKey(ImportSource.BCNESA, "CETT ST ANDREU DE LA BARCA"));
        assertEquals("ctt collbato",
                normalizer.exactKey(ImportSource.BCNESA, "CTT COLLBATO LA CASSOLA"));
        assertEquals("tt joves ctdfels",
                normalizer.exactKey(ImportSource.BCNESA, "ÀNECBLAU - TT ELS JOVES"));
    }

    @Test
    void preservesTheAccentedCanonicalDisplayNameForTerminalTeamVariants() {
        assertEquals("ctt la bisbal del penedes",
                normalizer.exactKey(ImportSource.BCNESA, "CTT LA BISBAL DEL PENEDÈS A"));
        assertEquals("ctt la bisbal del penedes",
                normalizer.exactKey(ImportSource.BCNESA, "CTT LA BISBAL DEL PENEDÈS B"));
        assertEquals("ctt colonia guell",
                normalizer.exactKey(ImportSource.BCNESA, "CTT COLÒNIA GÜELL A"));
        assertEquals("ctt colonia guell",
                normalizer.exactKey(ImportSource.BCNESA, "CTT COLÒNIA GÜELL B"));
    }

    @Test
    void infersAContextualRootForPreviouslyUnknownSuffixes() {
        List<String> names = List.of("CTT CLUB", "CTT CLUB NORTH", "CTT CLUB EAST");
        assertEquals("ctt club", normalizer.contextualKey(ImportSource.BCNESA, names.get(1), names));
        assertEquals("CTT CLUB", normalizer.preferredDisplayName(ImportSource.BCNESA, names));
    }

    @Test
    void infersTheRootWhenARegisteredCategoryContainsAnUnknownIntermediateToken() {
        List<String> names = List.of(
                "CTT SANT QUIRZE DEL VALLÈS - Sen A",
                "CTT ST QUIRZE DEL VALLÈS - Vet D A",
                "CTT ST QUIRZE DEL VALLÈS - Vet A");
        assertEquals("ctt sant quirze del valles",
                normalizer.contextualKey(ImportSource.BCNESA, names.get(1), names));
        assertEquals("CTT SANT QUIRZE DEL VALLÈS", normalizer.preferredDisplayName(ImportSource.BCNESA, names));
    }

    @Test
    void handlesQuotedTerminalTeamLettersAndAccentVariants() {
        assertEquals("tennis taula cassa",
                normalizer.exactKey(ImportSource.BCNESA, "TENNIS TAULA CASSÀ"));
        assertEquals("oberena",
                normalizer.exactKey(ImportSource.BCNESA, "OBERENA 'A'"));
        assertEquals("OBERENA",
                normalizer.preferredDisplayName(ImportSource.BCNESA, List.of("OBERENA 'A'", "OBERENA \"A\"")));
    }
}
