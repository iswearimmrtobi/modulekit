package gg.cubix.modulekit.folia;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.concurrent.TimeUnit;

/**
 * Region-aware scheduling scoped to one owner.
 *
 * Folia has no main thread — loaded chunks are split into regions that tick in
 * parallel, each with its own thread. Which scheduler you pick decides which
 * thread your code runs on, and therefore what data it may legally touch:
 *
 * <pre>
 *   scheduler().region(loc).run(() -&gt; loc.getBlock().setType(Material.STONE));
 *   scheduler().entity(player).runLater(() -&gt; player.setHealth(20), 20L);
 *   scheduler().global().runRepeating(this::tickScoreboardCache, 0L, 100L);
 *   scheduler().async().runNow(this::flushToDatabase);
 * </pre>
 *
 * Paper implements the same four schedulers on its single main thread, so code
 * written against this interface runs unchanged on both servers.
 *
 * Every handle returned here is tracked by the owner and cancelled when it shuts
 * down — the same guarantee {@code PaperModule.registerListener} gives listeners.
 */
public interface ModuleScheduler {

    /**
     * The global region — server-wide state with no location: the day/night cycle,
     * weather, the player list. Never touch region- or entity-owned data from here.
     */
    TickTasks global();

    /** The region owning {@code location}. Use for blocks and world edits, not entities. */
    TickTasks region(Location location);

    /** The region owning the given chunk. Use for blocks and world edits, not entities. */
    TickTasks region(World world, int chunkX, int chunkZ);

    /**
     * The region owning {@code entity}, following it as it moves between regions.
     * Use for anything touching an entity or player — never {@link #region(Location)}.
     */
    EntityTasks entity(Entity entity);

    /** A thread off the tick loops entirely. Use for I/O, HTTP and database work. */
    AsyncTasks async();

    /** Cancels every task this scheduler still tracks. */
    void cancelAll();

    /** How many scheduled tasks are still tracked. Diagnostics and tests. */
    int trackedTaskCount();

    /** Tick-based scheduling, shared by the global and region scopes. */
    interface TickTasks {

        /** Runs on the next tick of the target region. Fire and forget — nothing to cancel. */
        void run(Runnable task);

        /** Runs on the next tick of the target region, returning a handle. */
        Task runNow(Runnable task);

        /** Runs once after {@code delayTicks}. */
        Task runLater(Runnable task, long delayTicks);

        /** Runs every {@code periodTicks} after an initial {@code initialDelayTicks}. */
        Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks);
    }

    /**
     * Tick-based scheduling on an entity's region.
     *
     * Each method has an overload taking a {@code retired} callback, which Folia runs
     * instead of the task if the entity is removed before it fires. Without one, a task
     * scheduled against a removed entity is silently dropped and the returned handle is
     * {@link Task#none()}.
     */
    interface EntityTasks {

        Task runNow(Runnable task);

        Task runNow(Runnable task, Runnable retired);

        Task runLater(Runnable task, long delayTicks);

        Task runLater(Runnable task, Runnable retired, long delayTicks);

        Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks);

        Task runRepeating(Runnable task, Runnable retired, long initialDelayTicks, long periodTicks);
    }

    /**
     * Off-tick scheduling. Measured in real time rather than ticks, matching Paper's
     * {@code AsyncScheduler}. Nothing here may touch the Bukkit API without hopping
     * back onto a region through one of the other scopes.
     */
    interface AsyncTasks {

        Task runNow(Runnable task);

        Task runLater(Runnable task, long delay, TimeUnit unit);

        Task runRepeating(Runnable task, long initialDelay, long period, TimeUnit unit);
    }
}
