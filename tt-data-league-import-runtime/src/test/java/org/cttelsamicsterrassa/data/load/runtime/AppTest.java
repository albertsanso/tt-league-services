package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaTraversalSummary;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.player.consolidate.PlayerConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppTest {

    @Test
    void dispatchesEachSourceWithoutASeasonFilter() throws Exception {
        RecordingRfetmNavigator rfetm = new RecordingRfetmNavigator();
        RecordingBcnesaNavigator bcnesa = new RecordingBcnesaNavigator();
        RecordingFcttNavigator fctt = new RecordingFcttNavigator();
        App app = app(rfetm, bcnesa, fctt);

        app.run("--source=rfetm", "--actas-folder=C:\\data");
        app.run("--source=bcnesa", "--actas-folder=C:\\data");
        app.run("--source=fctt", "--actas-folder=C:\\data");

        assertEquals("all", rfetm.lastCall);
        assertEquals("all", bcnesa.lastCall);
        assertEquals("all", fctt.lastCall);
    }

    @Test
    void dispatchesTheSelectedSeasonToEachSourceNavigator() throws Exception {
        RecordingRfetmNavigator rfetm = new RecordingRfetmNavigator();
        RecordingBcnesaNavigator bcnesa = new RecordingBcnesaNavigator();
        RecordingFcttNavigator fctt = new RecordingFcttNavigator();
        App app = app(rfetm, bcnesa, fctt);

        app.run("--source=rfetm", "--actas-folder=C:\\data", "--season=2023-2024");
        app.run("--source=bcnesa", "--actas-folder=C:\\data", "--season=2023-2024");
        app.run("--source=fctt", "--actas-folder=C:\\data", "--season=2023-2024");

        assertEquals("2023-2024", rfetm.lastCall);
        assertEquals("2023-2024", bcnesa.lastCall);
        assertEquals("2023-2024", fctt.lastCall);
    }

    @Test
    void rejectsAnUnknownSourceBeforeTraversal() {
        RecordingRfetmNavigator rfetm = new RecordingRfetmNavigator();
        App app = app(rfetm, new RecordingBcnesaNavigator(), new RecordingFcttNavigator());

        assertThrows(
                IllegalArgumentException.class,
                () -> app.run("--source=other", "--actas-folder=C:\\data"));

        assertNull(rfetm.lastCall);
    }

    @Test
    void runsClubConsolidationBeforePlayerConsolidation() throws Exception {
        List<String> events = new ArrayList<>();
        RecordingFcttNavigator fctt = new RecordingFcttNavigator(events);
        RecordingClubRunner clubs = new RecordingClubRunner(events);
        RecordingPlayerRunner players = new RecordingPlayerRunner(events);
        App app = app(
                new RecordingRfetmNavigator(),
                new RecordingBcnesaNavigator(),
                fctt,
                clubs,
                players);

        app.run(
                "--source=fctt",
                "--actas-folder=C:\\data",
                "--consolidate-clubs=report",
                "--consolidate-players=report");

        assertEquals(List.of("traverse", "clubs", "players"), events);
        assertEquals(ImportSource.FCTT, clubs.source);
        assertEquals(ConsolidationMode.REPORT, clubs.mode);
        assertEquals(ImportSource.FCTT, players.source);
        assertEquals(ConsolidationMode.REPORT, players.mode);
    }

    private static App app(
            RecordingRfetmNavigator rfetm,
            RecordingBcnesaNavigator bcnesa,
            RecordingFcttNavigator fctt) {
        return app(
                rfetm,
                bcnesa,
                fctt,
                new ClubConsolidationRunner(null),
                new PlayerConsolidationRunner(null));
    }

    private static App app(
            RecordingRfetmNavigator rfetm,
            RecordingBcnesaNavigator bcnesa,
            RecordingFcttNavigator fctt,
            ClubConsolidationRunner clubs,
            PlayerConsolidationRunner players) {
        return new App(
                rfetm,
                bcnesa,
                fctt,
                clubs,
                players);
    }

    private static final class RecordingRfetmNavigator extends RfetmActasDirectoryNavigator {
        private String lastCall;

        private RecordingRfetmNavigator() {
            super(null, null);
        }

        @Override
        public TraversalSummary traverse(Path baseFolder) {
            lastCall = "all";
            return new TraversalSummary(0, 0, 0, 0);
        }

        @Override
        public TraversalSummary traverseSeason(Path baseFolder, String season) {
            lastCall = season;
            return new TraversalSummary(0, 0, 0, 0);
        }
    }

    private static final class RecordingBcnesaNavigator extends BcnesaActasDirectoryNavigator {
        private String lastCall;

        private RecordingBcnesaNavigator() {
            super(null, null);
        }

        @Override
        public BcnesaTraversalSummary traverse(Path baseFolder) {
            lastCall = "all";
            return new BcnesaTraversalSummary(0, 0, 0, 0, 0, 0);
        }

        @Override
        public BcnesaTraversalSummary traverseSeason(Path baseFolder, String season) {
            lastCall = season;
            return new BcnesaTraversalSummary(0, 0, 0, 0, 0, 0);
        }
    }

    private static final class RecordingFcttNavigator extends FcttActasDirectoryNavigator {
        private String lastCall;
        private final List<String> events;

        private RecordingFcttNavigator() {
            this(new ArrayList<>());
        }

        private RecordingFcttNavigator(List<String> events) {
            super(null, null);
            this.events = events;
        }

        @Override
        public TraversalSummary traverse(Path baseFolder) {
            lastCall = "all";
            events.add("traverse");
            return new TraversalSummary(0, 0, 0, 0);
        }

        @Override
        public TraversalSummary traverseSeason(Path baseFolder, String season) {
            lastCall = season;
            events.add("traverse");
            return new TraversalSummary(0, 0, 0, 0);
        }
    }

    private static final class RecordingClubRunner extends ClubConsolidationRunner {
        private final List<String> events;
        private ImportSource source;
        private ConsolidationMode mode;

        private RecordingClubRunner(List<String> events) {
            super(null);
            this.events = events;
        }

        @Override
        public ClubConsolidationSummary run(ImportSource source, ConsolidationMode mode) {
            this.source = source;
            this.mode = mode;
            events.add("clubs");
            return ClubConsolidationSummary.disabled(source, "test");
        }
    }

    private static final class RecordingPlayerRunner extends PlayerConsolidationRunner {
        private final List<String> events;
        private ImportSource source;
        private ConsolidationMode mode;

        private RecordingPlayerRunner(List<String> events) {
            super(null);
            this.events = events;
        }

        @Override
        public PlayerConsolidationSummary run(ImportSource source, ConsolidationMode mode) {
            this.source = source;
            this.mode = mode;
            events.add("players");
            return PlayerConsolidationSummary.builder(source).build();
        }
    }
}
