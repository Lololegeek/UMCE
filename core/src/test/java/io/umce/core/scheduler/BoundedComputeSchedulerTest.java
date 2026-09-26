package io.umce.core.scheduler;

import io.umce.api.scheduler.TaskSafety;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BoundedComputeSchedulerTest {
    @Test
    void onlyExplicitlyThreadSafeTasksRunOnWorkers() throws Exception {
        try (BoundedComputeScheduler scheduler = new BoundedComputeScheduler(2, 4)) {
            String worker = scheduler.submit("test", TaskSafety.THREAD_SAFE,
                    () -> Thread.currentThread().getName()).get(3, TimeUnit.SECONDS);
            assertTrue(worker.startsWith("umce-compute-"));

            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> scheduler.submit("unknown", TaskSafety.UNKNOWN, () -> 1).get());
            assertInstanceOf(RejectedExecutionException.class, rejected.getCause());
        }
    }

    @Test
    void boundedQueueAppliesBackpressureInsteadOfGrowingWithoutLimit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (BoundedComputeScheduler scheduler = new BoundedComputeScheduler(1, 1)) {
            java.util.concurrent.CompletableFuture<Integer> first = scheduler.submit("test", TaskSafety.THREAD_SAFE, () -> {
                started.countDown();
                release.await();
                return 1;
            });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            java.util.concurrent.CompletableFuture<Integer> queued = scheduler.submit("test", TaskSafety.THREAD_SAFE, () -> 2);
            java.util.concurrent.CompletableFuture<Integer> overflow = scheduler.submit("test", TaskSafety.THREAD_SAFE, () -> 3);
            assertEquals(1, scheduler.getQueuedTaskCount());
            assertInstanceOf(RejectedExecutionException.class,
                    assertThrows(ExecutionException.class, overflow::get).getCause());
            release.countDown();
            assertEquals(1, first.get(3, TimeUnit.SECONDS));
            assertEquals(2, queued.get(3, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }
}
