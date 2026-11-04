package org.cttelsamicsterrassa.data.load.runtime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillWriteResult;
import org.cttelsamicsterrassa.data.core.domain.match.repository.ScheduledMatchBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaTraversalSummary;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionMetrics;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionResult;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionService;
import org.cttelsamicsterrassa.data.load.shared.match.backfill.ScheduledMatchBackfillService;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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

    private static App app(
            RecordingRfetmNavigator rfetm,
            RecordingBcnesaNavigator bcnesa,
            RecordingFcttNavigator fctt) {
        return new App(rfetm, bcnesa, fctt);
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

    @Test
    void backfillDispatchesReportAndWriteWithTheParsedSourceAndSeason() {
        RecordingBackfillRepository repository = new RecordingBackfillRepository();
        FailingImportExecutionService executionService = new FailingImportExecutionService();
        App app = new App(executionService, new ScheduledMatchBackfillService(repository));

        app.run("--source=fctt", "--season=2025-2026", "--backfill-scheduled-matches=report");

        assertEquals(ImportSource.FCTT, repository.lastSource);
        assertEquals(Season.of(2025, 2026), repository.lastSeason);
        assertFalse(repository.markScheduledCalled);

        app.run("--source=fctt", "--season=2025-2026", "--backfill-scheduled-matches");

        assertTrue(repository.markScheduledCalled);
        assertFalse(executionService.called);
    }

    @Test
    void backfillWithoutASeasonIsRejected() {
        App app = new App(new FailingImportExecutionService(),
                new ScheduledMatchBackfillService(new RecordingBackfillRepository()));

        assertThrows(IllegalArgumentException.class,
                () -> app.run("--backfill-scheduled-matches"));
    }

    @Test
    void backfillWithAMalformedSeasonIsRejected() {
        App app = new App(new FailingImportExecutionService(),
                new ScheduledMatchBackfillService(new RecordingBackfillRepository()));

        assertThrows(IllegalArgumentException.class,
                () -> app.run("--season=2025", "--backfill-scheduled-matches"));
        assertThrows(IllegalArgumentException.class,
                () -> app.run("--season=2025-2027", "--backfill-scheduled-matches"));
    }

    @Test
    void backfillCombinedWithImportArgumentsIsRejected() {
        App app = new App(new FailingImportExecutionService(),
                new ScheduledMatchBackfillService(new RecordingBackfillRepository()));

        assertThrows(IllegalArgumentException.class,
                () -> app.run("--season=2025-2026", "--actas-folder=C:\\data", "--backfill-scheduled-matches"));
        assertThrows(IllegalArgumentException.class,
                () -> app.run("--season=2025-2026", "--consolidate-clubs", "--backfill-scheduled-matches"));
    }

    @Test
    void logsOneJornadaProgressLinePerStoredRound() {
        ListAppender<ILoggingEvent> appender = attachToAppLogger();
        try {
            App app = appWithProgress(List.of(
                    new RoundProgress(ImportSource.FCTT, Season.of(2026), "tercera-nacional-masculino",
                            1, "1a Fase", 1, null, 9, 3),
                    new RoundProgress(ImportSource.FCTT, Season.of(2026), "grup-sense-clau",
                            null, null, null, null, 4, 0)));

            app.run("--source=fctt", "--actas-folder=C:\\data", "--season=2026-2027");

            List<String> messages = loggedMessages(appender);
            assertTrue(messages.contains("FCTT/2026-2027 progress tercera-nacional-masculino G1 1a Fase: "
                    + "current round 1, last complete round -, scheduled 9, played 3"), messages.toString());
            assertTrue(messages.contains("FCTT/2026-2027 progress grup-sense-clau G- -: "
                    + "current round -, last complete round -, scheduled 4, played 0"), messages.toString());
            assertEquals(2, messages.stream().filter(message -> message.contains("current round")).count());
        } finally {
            detachFromAppLogger(appender);
        }
    }

    @Test
    void aRunWithoutASeasonSaysTheProgressWasNotComputed() {
        ListAppender<ILoggingEvent> appender = attachToAppLogger();
        try {
            App app = appWithProgress(List.of(new RoundProgress(ImportSource.FCTT, Season.of(2026),
                    "tercera-nacional-masculino", 1, null, 1, 1, 0, 6)));

            app.run("--source=fctt", "--actas-folder=C:\\data");

            List<String> messages = loggedMessages(appender);
            assertTrue(messages.contains("FCTT round progress not computed: run without --season"),
                    messages.toString());
            assertEquals(0, messages.stream().filter(message -> message.contains("current round")).count());
        } finally {
            detachFromAppLogger(appender);
        }
    }

    @Test
    void aSeasonWithoutStoredMatchesSaysSoInsteadOfLoggingNothing() {
        ListAppender<ILoggingEvent> appender = attachToAppLogger();
        try {
            App app = appWithProgress(List.of());

            app.run("--source=fctt", "--actas-folder=C:\\data", "--season=2026-2027");

            assertTrue(loggedMessages(appender)
                    .contains("FCTT/2026-2027 round progress: no stored matches"));
        } finally {
            detachFromAppLogger(appender);
        }
    }

    /** An import that stores nothing new but reports the given progress rows for the requested season. */
    private static App appWithProgress(List<RoundProgress> roundProgress) {
        return new App((request, options) -> new ImportExecutionResult(
                        request.source(), request.season().map(Object::toString), ImportProcessStatus.SUCCESS,
                        new ImportExecutionMetrics(12, 12, 0, 0, 0, 1), List.of(), List.of(), List.of(),
                        roundProgress),
                new ScheduledMatchBackfillService(new RecordingBackfillRepository()));
    }

    private static ListAppender<ILoggingEvent> attachToAppLogger() {
        Logger appLogger = (Logger) LoggerFactory.getLogger(App.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        appLogger.addAppender(appender);
        return appender;
    }

    private static void detachFromAppLogger(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(App.class)).detachAppender(appender);
        appender.stop();
    }

    private static List<String> loggedMessages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static final class RecordingBackfillRepository implements ScheduledMatchBackfillRepository {
        private ImportSource lastSource;
        private Season lastSeason;
        private boolean markScheduledCalled;

        @Override
        public List<ScheduledMatchBackfillCandidate> findScheduledBackfillCandidates(
                ImportSource source, Season season) {
            lastSource = source;
            lastSeason = season;
            return List.of();
        }

        @Override
        public ScheduledMatchBackfillWriteResult markScheduled(
                ImportSource source, Season season, Collection<UUID> matchIds) {
            lastSource = source;
            lastSeason = season;
            markScheduledCalled = true;
            return new ScheduledMatchBackfillWriteResult(0, 0, 0, 0, 0);
        }
    }

    private static final class FailingImportExecutionService implements ImportExecutionService {
        private boolean called;

        @Override
        public org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionResult execute(
                org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionRequest request,
                org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions options) {
            called = true;
            throw new AssertionError("Import must not run during a backfill");
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


}
