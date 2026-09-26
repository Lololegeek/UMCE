package io.umce.api.scheduler;

public enum TaskSafety {
    UNKNOWN,
    MAIN_THREAD_ONLY,
    REGION_SAFE,
    THREAD_SAFE
}
