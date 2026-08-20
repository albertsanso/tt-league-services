package org.cttelsamicsterrassa.data.load.traverse;

import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaClubIndex;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BcnesaClubIndexTest {

    @TempDir
    Path groupFolder;

    private final ActaParser parser = new ActaParser();

    @Test
    void resolvesAClubFromTheMajorityOfItsPlayersLicenceVotes() throws IOException {
        Path phase = Files.createDirectories(groupFolder.resolve("1a Fase"));
        // Licence 30 plays away (visitante.X) for AWAY CLUB in three files and, once, is
        // misattributed to a different club in a fourth - the majority vote must still land on
        // AWAY CLUB.
        Files.writeString(phase.resolve("acta_1.json"), acta("HOME CLUB", "AWAY CLUB", "10", "20", "30", "40"));
        Files.writeString(phase.resolve("acta_2.json"), acta("HOME CLUB", "AWAY CLUB", "11", "21", "30", "41"));
        Files.writeString(phase.resolve("acta_3.json"), acta("HOME CLUB", "AWAY CLUB", "12", "22", "30", "42"));
        Files.writeString(phase.resolve("acta_4.json"), acta("A DIFFERENT CLUB", "YET ANOTHER", "13", "23", "30", "43"));

        BcnesaClubIndex index = BcnesaClubIndex.build(groupFolder, parser);

        assertEquals(Optional.of("HOME CLUB"), index.resolve(List.of("10", "20")));
        assertEquals(Optional.of("AWAY CLUB"), index.resolve(List.of("30")));
    }

    @Test
    void leavesAnUnknownLicenceUnresolved() throws IOException {
        Path phase = Files.createDirectories(groupFolder.resolve("1a Fase"));
        Files.writeString(phase.resolve("acta_1.json"), acta("HOME CLUB", "AWAY CLUB", "10", "20", "30", "40"));

        BcnesaClubIndex index = BcnesaClubIndex.build(groupFolder, parser);

        assertTrue(index.resolve(List.of("no-such-licence")).isEmpty());
    }

    private static String acta(String home, String away, String a, String b, String c, String x) {
        return """
                {
                  "federacion": "Federació Catalana de Tennis Taula",
                  "temporada": "2023/2024",
                  "competicion": "Preferent",
                  "grupo": 1,
                  "jornada": 1,
                  "fecha": "2023-10-01",
                  "hora": null,
                  "lugar": null,
                  "equipos": {
                    "local": { "id": null, "nombre": "%s", "delegado": null, "entrenador": null },
                    "visitante": { "id": null, "nombre": "%s", "delegado": null, "entrenador": null }
                  },
                  "abc_es_local": true,
                  "arbitros": { "principal": null, "asistente": null },
                  "alineaciones": {
                    "local": {
                      "A": { "licencia": "%s", "nombre": "HOME A" },
                      "B": { "licencia": "%s", "nombre": "HOME B" }
                    },
                    "visitante": {
                      "X": { "licencia": "%s", "nombre": "AWAY X" },
                      "Y": { "licencia": "%s", "nombre": "AWAY Y" }
                    }
                  },
                  "dobles": null,
                  "partidos": [],
                  "resultado_final": { "ganador": null, "marcador_partidos": { "local": 0, "visitante": 0 }, "marcador_juegos": null },
                  "acta_protestada": false
                }
                """.formatted(home, away, a, b, c, x);
    }
}
