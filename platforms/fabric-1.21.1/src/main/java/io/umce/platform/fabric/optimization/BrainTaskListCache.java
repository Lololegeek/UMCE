package io.umce.platform.fabric.optimization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lazily flattens the active Brain task sets; owned and accessed by the server thread. */
public final class BrainTaskListCache<A, T> {
    private List<T> activeTasks;

    public List<T> get(Map<Integer, Map<A, Set<T>>> tasks, Set<A> possibleActivities) {
        List<T> cached = activeTasks;
        if (cached != null) return cached;

        List<T> flattened = new ArrayList<T>();
        for (Map<A, Set<T>> tasksByActivity : tasks.values()) {
            for (Map.Entry<A, Set<T>> activityTasks : tasksByActivity.entrySet()) {
                if (possibleActivities.contains(activityTasks.getKey())) {
                    flattened.addAll(activityTasks.getValue());
                }
            }
        }
        activeTasks = flattened;
        return flattened;
    }

    public void invalidate() {
        activeTasks = null;
    }
}
