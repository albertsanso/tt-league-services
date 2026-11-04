package org.cttelsamicsterrassa.data.api.runtime.importjob;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorImportJobDispatcherTest {

    @Test
    void runsJobsOneAtATimeInDispatchOrderOnItsOwnThread() throws Exception {
        List<UUID> started = new CopyOnWriteArrayList<>();
        List<String> threads = new CopyOnWriteArrayList<>();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(3);
        ExecutorImportJobDispatcher dispatcher = new ExecutorImportJobDispatcher(jobId -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            started.add(jobId);
            threads.add(Thread.currentThread().getName());
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            running.decrementAndGet();
            done.countDown();
        });
        List<UUID> jobs = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        jobs.forEach(dispatcher::dispatch);

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(jobs, started);
        assertEquals(1, maxRunning.get());
        assertTrue(threads.stream().allMatch(ExecutorImportJobDispatcher.THREAD_NAME::equals));
        dispatcher.destroy();
    }

    @Test
    void aFailingJobDoesNotStopTheNextOne() throws Exception {
        CountDownLatch secondRan = new CountDownLatch(1);
        UUID failing = UUID.randomUUID();
        ExecutorImportJobDispatcher dispatcher = new ExecutorImportJobDispatcher(jobId -> {
            if (jobId.equals(failing)) {
                throw new IllegalStateException("database down");
            }
            secondRan.countDown();
        });

        dispatcher.dispatch(failing);
        dispatcher.dispatch(UUID.randomUUID());

        assertTrue(secondRan.await(5, TimeUnit.SECONDS));
        dispatcher.destroy();
    }

    @Test
    void rejectsDispatchAfterShutdown() {
        ExecutorImportJobDispatcher dispatcher = new ExecutorImportJobDispatcher(jobId -> { });
        dispatcher.destroy();

        assertThrows(RejectedExecutionException.class, () -> dispatcher.dispatch(UUID.randomUUID()));
    }
}
