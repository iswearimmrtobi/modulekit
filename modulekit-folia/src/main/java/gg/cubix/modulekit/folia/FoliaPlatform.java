package gg.cubix.modulekit.folia;

import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

/**
 * Platform detection and region-ownership helpers.
 *
 * Every method here resolves through the Paper API, which ships the regionised
 * scheduler contracts on both Paper and Folia — so nothing in this class branches
 * on the running server. {@link #isFolia()} exists for the cases where a module
 * genuinely needs to know (logging, or picking a different algorithm entirely),
 * not as a prerequisite for scheduling.
 */
public final class FoliaPlatform {

    /** Brand key Folia reports through {@link ServerBuildInfo}. */
    public static final Key BRAND_FOLIA_ID = Key.key("papermc", "folia");

    private static volatile Boolean folia;
    private static volatile Boolean paper;

    private FoliaPlatform() {}

    /**
     * True when running on Folia (or a Folia-compatible fork).
     *
     * Resolved lazily on first call rather than in a static initialiser: this class
     * may well be loaded before the server has registered its {@code ServerBuildInfo}
     * service. Any failure — an older API, a non-Paper server — is treated as "not Folia".
     */
    public static boolean isFolia() {
        Boolean cached = folia;
        if (cached == null) {
            cached = isBrandCompatible(BRAND_FOLIA_ID);
            folia = cached;
        }
        return cached;
    }

    /** True when running on Paper (Folia reports compatible with both). */
    public static boolean isPaper() {
        Boolean cached = paper;
        if (cached == null) {
            cached = isBrandCompatible(ServerBuildInfo.BRAND_PAPER_ID);
            paper = cached;
        }
        return cached;
    }

    /** Human-readable platform name for startup logging. */
    public static String describe() {
        return isFolia() ? "Folia (regionised multithreading)" : "Paper";
    }

    /** Clears the memoised brand lookups. Test seam — the brand cannot change at runtime. */
    static void resetCache() {
        folia = null;
        paper = null;
    }

    private static boolean isBrandCompatible(Key brand) {
        try {
            return ServerBuildInfo.buildInfo().isBrandCompatible(brand);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // --- Region ownership -------------------------------------------------
    // On Folia these tell you whether the current thread may touch the data in
    // question. On Paper they answer for the single main thread, so the same
    // guard code is correct on both.

    /** True if the current thread owns the region ticking {@code location}. */
    public static boolean isOwnedByCurrentRegion(Location location) {
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    /** True if the current thread owns the region ticking the given chunk. */
    public static boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ) {
        return Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }

    /** True if the current thread owns the region ticking {@code entity}. */
    public static boolean isOwnedByCurrentRegion(Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    /** True if the current thread owns the region ticking {@code block}. */
    public static boolean isOwnedByCurrentRegion(Block block) {
        return Bukkit.isOwnedByCurrentRegion(block);
    }

    /** True if the current thread is the global region thread. */
    public static boolean isGlobalTickThread() {
        return Bukkit.isGlobalTickThread();
    }

    // --- Raw schedulers ---------------------------------------------------
    // Escape hatches for authors who need the platform types directly. Tasks
    // started through these are NOT tracked by ModuleScheduler — you cancel them.

    public static GlobalRegionScheduler globalScheduler() {
        return Bukkit.getGlobalRegionScheduler();
    }

    public static RegionScheduler regionScheduler() {
        return Bukkit.getRegionScheduler();
    }

    public static AsyncScheduler asyncScheduler() {
        return Bukkit.getAsyncScheduler();
    }
}
