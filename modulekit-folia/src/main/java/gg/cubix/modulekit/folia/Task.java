package gg.cubix.modulekit.folia;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * A cancellable handle to a scheduled task.
 *
 * Thin wrapper over Paper's {@link ScheduledTask} so module code does not have to
 * import the {@code threadedregions} package, and so the entity scope can return a
 * usable object instead of {@code null} for an entity that has already been removed.
 */
public interface Task {

    /** Cancels this task, or its remaining runs if it repeats. Safe to call more than once. */
    void cancel();

    /** True once this task has been cancelled. */
    boolean isCancelled();

    /** True if this task was scheduled to repeat. */
    boolean isRepeating();

    /**
     * A handle for a task that was never scheduled — returned by the entity scope when
     * the target entity has been removed, since Folia schedules nothing for a retired entity.
     */
    static Task none() {
        return NoTask.INSTANCE;
    }

    /** Wraps a platform task. */
    static Task of(ScheduledTask task) {
        return task == null ? none() : new PlatformTask(task);
    }

    /** @return the underlying platform task, or {@code null} if nothing was scheduled. */
    ScheduledTask handle();

    final class PlatformTask implements Task {
        private final ScheduledTask delegate;

        PlatformTask(ScheduledTask delegate) {
            this.delegate = delegate;
        }

        @Override public void cancel()           { delegate.cancel(); }
        @Override public boolean isCancelled()   { return delegate.isCancelled(); }
        @Override public boolean isRepeating()   { return delegate.isRepeatingTask(); }
        @Override public ScheduledTask handle()  { return delegate; }
    }

    final class NoTask implements Task {
        static final NoTask INSTANCE = new NoTask();

        private NoTask() {}

        @Override public void cancel()           { /* nothing was scheduled */ }
        @Override public boolean isCancelled()   { return true; }
        @Override public boolean isRepeating()   { return false; }
        @Override public ScheduledTask handle()  { return null; }
    }
}
