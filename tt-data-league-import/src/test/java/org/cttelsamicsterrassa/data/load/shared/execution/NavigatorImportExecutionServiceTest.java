package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focuses on the progress-listener overload added for FEAT-00032: the synchronous, listener-less
 * {@code execute(...)} keeps producing the same result it always has, while the listener overload
 * additionally reports at least one progress snapshot without changing that outcome.
 */
class NavigatorImportExecutionServiceTest {

    @TempDir
    Path baseFolder;

    private NavigatorImportExecutionService service;

    @BeforeEach
    void setUp() {
        ActaParser parser = new ActaParser();
        service = new NavigatorImportExecutionService(
                new RfetmActasDirectoryNavigator(List.of(), parser),
                new BcnesaActasDirectoryNavigator(List.of(), parser),
                new FcttActasDirectoryNavigator(List.of(), parser),
                List.of(), List.of(), List.of());
    }

    @Test
    void theListenerLessOverloadKeepsWorkingUnchanged() throws IOException {
        writeRfetmReport("2023-2024", "super-divisio", "1", "masculino");

        ImportExecutionResult result = service.execute(
                new ImportExecutionRequest(ImportSource.RFETM, baseFolder, Optional.of(Season.of(2023))),
                ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.SUCCESS, result.status());
        assertEquals(1, result.metrics().itemsDispatched());
    }

    @Test
    void theListenerOverloadReportsProgressWithoutChangingTheResult() throws IOException {
        writeRfetmReport("2023-2024", "super-divisio", "1", "masculino");
        List<ImportRunProgress> updates = new ArrayList<>();

        ImportExecutionResult result = service.execute(
                new ImportExecutionRequest(ImportSource.RFETM, baseFolder, Optional.of(Season.of(2023))),
                ImportExecutionOptions.defaults(), updates::add);

        assertEquals(ImportProcessStatus.SUCCESS, result.status());
        assertFalse(updates.isEmpty(), "at least the final progress snapshot must be reported");
        assertTrue(updates.getLast().total().isEmpty() || updates.getLast().processed() >= 0);
    }

    @Test
    void aTraversalFailureIsReportedNeitherAsSuccessNorSilently() throws IOException {
        Path missing = baseFolder.resolve("does-not-exist");

        ImportExecutionResult result = service.execute(
                new ImportExecutionRequest(ImportSource.RFETM, missing, Optional.empty()),
                ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.FAILURE, result.status());
        assertFalse(result.issues().isEmpty());
    }

    private void writeRfetmReport(String season, String competition, String day, String sex) throws IOException {
        Path folder = Files.createDirectories(
                baseFolder.resolve(season).resolve(competition).resolve(day).resolve(sex));
        Files.writeString(folder.resolve("acta.json"), """
                {
                  "federacion": "Real Federación Española de Tenis de Mesa",
                  "temporada": "2023/2024",
                  "competicion": "Temporada 2023-2024",
                  "grupo": 0,
                  "jornada": 1,
                  "fecha": "2023-09-29",
                  "hora": "19:00",
                  "lugar": { "recinto": "PABELLON PEREZ PUIG", "ciudad": "Alzira (Valencia)" },
                  "equipos": {
                    "local": { "nombre": "HOME CLUB", "id": "1", "delegado": null, "entrenador": null },
                    "visitante": { "nombre": "AWAY CLUB", "id": "2", "delegado": null, "entrenador": null }
                  },
                  "abc_es_local": false,
                  "arbitros": { "principal": null, "asistente": null },
                  "alineaciones": {
                    "local": { "X": { "nombre": "A, A", "licencia": "1", "id": "1" } },
                    "visitante": { "A": { "nombre": "B, B", "licencia": "2", "id": "2" } }
                  },
                  "dobles": null,
                  "partidos": [],
                  "resultado_final": {
                    "ganador": null,
                    "marcador_partidos": { "local": 0, "visitante": 0 },
                    "marcador_juegos": { "local": 0, "visitante": 0 }
                  },
                  "acta_protestada": false
                }
                """);
    }
}
