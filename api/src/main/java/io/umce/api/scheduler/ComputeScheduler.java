package io.umce.api.scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** Parallel submission API. Implementations must reject UNKNOWN and unsupported safety modes. */
public interface ComputeScheduler extends AutoCloseable {
    <T> CompletableFuture<T> submit(String owner, TaskSafety safety, Callable<T> task);
    int getWorkerCount();
    int getQueuedTaskCount();
    @Override
    void close();
}
