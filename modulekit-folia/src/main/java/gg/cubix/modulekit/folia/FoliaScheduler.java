package gg.cubix.modulekit.folia;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ModuleScheduler} backed by Paper's regionised schedulers.
 *
 * One instance per owner — each {@link FoliaModule} builds its own, and
 * {@link FoliaModuleManager} keeps a plugin-scoped one for services that are not
 * themselves modules. Handles are tracked so the owner can cancel everything it
 * started in a single call.
 */
public final class FoliaScheduler implements ModuleScheduler {

    /** Only sweep completed handles once the set has grown past this. */
    private static final int PRUNE_THRESHOLD = 32;

    private final JavaPlugin plugin;
    private final Logger logger;

    /**
     * Concurrent by necessity: on Folia, tasks are scheduled and completed from many
     * region threads at once, so the plain {@code ArrayList} that tracks listeners in
     * {@code PaperModule} would not be safe here.
     */
    private final Set<ScheduledTask> tracked = ConcurrentHashMap.newKeySet();

    public FoliaScheduler(JavaPlugin plugin) {
        this(plugin, plugin.getLogger());
    }

    public FoliaScheduler(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger != null ? logger : Logger.getLogger("modulekit-folia");
    }

    @Override
    public TickTasks global() {
        return new GlobalScope();
    }

    @Override
    public TickTasks region(Location location) {
        return new LocationScope(location);
    }

    @Override
    public TickTasks region(World world, int chunkX, int chunkZ) {
        return new ChunkScope(world, chunkX, chunkZ);
    }

    @Override
    public EntityTasks entity(Entity entity) {
        return new EntityScope(entity);
    }

    @Override
    public AsyncTasks async() {
        return new AsyncScope();
    }

    @Override
    public void cancelAll() {
        for (ScheduledTask task : Set.copyOf(tracked)) {
            try {
                task.cancel();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ModuleKit] Failed to cancel a scheduled task", t);
            }
        }
        tracked.clear();
    }

    @Override
    public int trackedTaskCount() {
        prune();
        return tracked.size();
    }

    /** True once this scheduler has no tracked task left to cancel. */
    public boolean isIdle() {
        return trackedTaskCount() == 0;
    }

    // --- Bookkeeping ------------------------------------------------------

    private Task track(ScheduledTask scheduled) {
        if (scheduled == null) return Task.none();   // entity was already retired
        tracked.add(scheduled);
        return Task.of(scheduled);
    }

    /**
     * Drops handles that have run to completion so a long-lived module does not
     * accumulate them. Done by sweeping rather than by removing from inside the task
     * body: a task can start executing before {@link #track} has even added it, and a
     * self-removing body would then leak the handle it raced past.
     */
    private void prune() {
        tracked.removeIf(task -> {
            ScheduledTask.ExecutionState state = task.getExecutionState();
            return state == ScheduledTask.ExecutionState.FINISHED
                || state == ScheduledTask.ExecutionState.CANCELLED;
        });
    }

    /**
     * Sweeps only once the set has grown enough to be worth walking. Scheduling is the
     * hot path — a module firing a task every tick should not pay an O(n) sweep each time.
     */
    private void pruneIfLarge() {
        if (tracked.size() >= PRUNE_THRESHOLD) prune();
    }

    /**
     * Adapts a user {@link Runnable} to the platform's {@code Consumer<ScheduledTask>}
     * callback, containing any exception it throws. An exception escaping into a Folia
     * region tick is considerably worse than one on Paper's single main thread, so it is
     * logged and swallowed rather than allowed to propagate.
     */
    private Consumer<ScheduledTask> adapt(Runnable task) {
        return scheduled -> guard(task);
    }

    private void guard(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ModuleKit] Scheduled task threw an exception", t);
        }
    }

    // --- Scopes -----------------------------------------------------------

    private final class GlobalScope implements TickTasks {
        @Override
        public void run(Runnable task) {
            FoliaPlatform.globalScheduler().execute(plugin, () -> guard(task));
        }

        @Override
        public Task runNow(Runnable task) {
            pruneIfLarge();
            return track(FoliaPlatform.globalScheduler().run(plugin, adapt(task)));
        }

        @Override
        public Task runLater(Runnable task, long delayTicks) {
            pruneIfLarge();
            return track(FoliaPlatform.globalScheduler().runDelayed(plugin, adapt(task), delayTicks));
        }

        @Override
        public Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
            pruneIfLarge();
            return track(FoliaPlatform.globalScheduler()
                .runAtFixedRate(plugin, adapt(task), initialDelayTicks, periodTicks));
        }
    }

    private final class LocationScope implements TickTasks {
        private final Location location;

        LocationScope(Location location) {
            this.location = location;
        }

        @Override
        public void run(Runnable task) {
            FoliaPlatform.regionScheduler().execute(plugin, location, () -> guard(task));
        }

        @Override
        public Task runNow(Runnable task) {
            pruneIfLarge();
            return track(FoliaPlatform.regionScheduler().run(plugin, location, adapt(task)));
        }

        @Override
        public Task runLater(Runnable task, long delayTicks) {
            pruneIfLarge();
            return track(FoliaPlatform.regionScheduler()
                .runDelayed(plugin, location, adapt(task), delayTicks));
        }

        @Override
        public Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
            pruneIfLarge();
            return track(FoliaPlatform.regionScheduler()
                .runAtFixedRate(plugin, location, adapt(task), initialDelayTicks, periodTicks));
        }
    }

    private final class ChunkScope implements TickTasks {
        private final World world;
        private final int chunkX;
        private final int chunkZ;

        ChunkScope(World world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public void run(Runnable task) {
            FoliaPlatform.regionScheduler().execute(plugin, world, chunkX, chunkZ, () -> guard(task));
        }

        @Override
        public Task runNow(Runnable task) {
            pruneIfLarge();
            return track(FoliaPlatform.regionScheduler().run(plugin, world, chunkX, chunkZ, adapt(task)));
        }

        @Override
        public Task runLater(Runnable task, long delayTicks) {
            pruneIfLarge();
            return track(FoliaPlatform.regionScheduler()
                .runDelayed(plugin, world, chunkX, chunkZ, adapt(task), delayTicks));
        }

        @Override
        public Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
            pruneIfLarge();
            return track(FoliaPlatform.regionScheduler()
                .runAtFixedRate(plugin, world, chunkX, chunkZ, adapt(task), initialDelayTicks, periodTicks));
        }
    }

    private final class EntityScope implements EntityTasks {
        private final Entity entity;

        EntityScope(Entity entity) {
            this.entity = entity;
        }

        @Override
        public Task runNow(Runnable task) {
            return runNow(task, null);
        }

        @Override
        public Task runNow(Runnable task, Runnable retired) {
            pruneIfLarge();
            return track(entity.getScheduler().run(plugin, adapt(task), retiredHook(retired)));
        }

        @Override
        public Task runLater(Runnable task, long delayTicks) {
            return runLater(task, null, delayTicks);
        }

        @Override
        public Task runLater(Runnable task, Runnable retired, long delayTicks) {
            pruneIfLarge();
            return track(entity.getScheduler()
                .runDelayed(plugin, adapt(task), retiredHook(retired), delayTicks));
        }

        @Override
        public Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
            return runRepeating(task, null, initialDelayTicks, periodTicks);
        }

        @Override
        public Task runRepeating(Runnable task, Runnable retired, long initialDelayTicks, long periodTicks) {
            pruneIfLarge();
            return track(entity.getScheduler()
                .runAtFixedRate(plugin, adapt(task), retiredHook(retired), initialDelayTicks, periodTicks));
        }

        /** The retired callback is nullable on the platform side — keep it that way. */
        private Runnable retiredHook(Runnable retired) {
            return retired == null ? null : () -> guard(retired);
        }
    }

    private final class AsyncScope implements AsyncTasks {
        @Override
        public Task runNow(Runnable task) {
            pruneIfLarge();
            return track(FoliaPlatform.asyncScheduler().runNow(plugin, adapt(task)));
        }

        @Override
        public Task runLater(Runnable task, long delay, TimeUnit unit) {
            pruneIfLarge();
            return track(FoliaPlatform.asyncScheduler().runDelayed(plugin, adapt(task), delay, unit));
        }

        @Override
        public Task runRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
            pruneIfLarge();
            return track(FoliaPlatform.asyncScheduler()
                .runAtFixedRate(plugin, adapt(task), initialDelay, period, unit));
        }
    }
}
