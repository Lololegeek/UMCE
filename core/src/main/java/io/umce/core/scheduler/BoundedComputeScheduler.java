package io.umce.core.scheduler;

import io.umce.api.scheduler.ComputeScheduler;
import io.umce.api.scheduler.TaskSafety;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded worker pool for explicitly thread-safe, platform-independent work. */
public final class BoundedComputeScheduler implements ComputeScheduler {
    private final ThreadPoolExecutor executor;

    public BoundedComputeScheduler(int workerCount, int queueCapacity) {
        if (workerCount < 1) throw new IllegalArgumentException("workerCount must be positive");
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be positive");
        this.executor = new ThreadPoolExecutor(workerCount, workerCount, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(queueCapacity), new WorkerFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public <T> CompletableFuture<T> submit(String owner, TaskSafety safety, Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<T>();
        if (owner == null || owner.trim().isEmpty()) {
            result.completeExceptionally(new IllegalArgumentException("owner must not be blank"));
            return result;
        }
        if (safety != TaskSafety.THREAD_SAFE) {
            result.completeExceptionally(new RejectedExecutionException(
                    "Parallel scheduler requires THREAD_SAFE work; received " + safety));
            return result;
        }
        if (task == null) {
            result.completeExceptionally(new IllegalArgumentException("task must not be null"));
            return result;
        }
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        result.complete(task.call());
                    } catch (Exception exception) {
                        result.completeExceptionally(exception);
                    } catch (Error error) {
                        result.completeExceptionally(error);
                        throw error;
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    @Override
    public int getWorkerCount() {
        return executor.getCorePoolSize();
    }

    @Override
    public int getQueuedTaskCount() {
        return executor.getQueue().size();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class WorkerFactory implements ThreadFactory {
        private final AtomicInteger nextId = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "umce-compute-" + nextId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
