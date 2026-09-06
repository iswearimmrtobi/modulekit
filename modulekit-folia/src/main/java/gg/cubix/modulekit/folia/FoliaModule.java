package gg.cubix.modulekit.folia;

import gg.cubix.modulekit.paper.PaperModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base class for modules that run on Folia as well as Paper.
 *
 * Everything {@link PaperModule} provides — the three-phase lifecycle, listener
 * bookkeeping, service registration, Brigadier command declarations — is inherited
 * unchanged, because none of it depends on a main thread. What this class adds is the
 * one thing Folia takes away: a scheduler.
 *
 * <pre>
 *   public final class BeaconModule extends FoliaModule {
 *
 *       public BeaconModule(JavaPlugin plugin) { super(plugin); }
 *
 *       public static ModuleDescriptor getDescriptor() {
 *           return ModuleDescriptor.builder("beacon", "Beacon")
 *               .requires(JavaPlugin.class)
 *               .build();
 *       }
 *
 *       {@literal @}Override public ModuleDescriptor descriptor() { return getDescriptor(); }
 *
 *       {@literal @}Override
 *       public void onEnable() {
 *           registerListener(new BeaconListener());
 *           scheduler().global().runRepeating(this::sweep, 0L, 200L);
 *       }
 *
 *       private void relight(Location where) {
 *           scheduler().region(where).run(() -&gt; where.getBlock().setType(Material.BEACON));
 *       }
 *   }
 * </pre>
 *
 * @see ModuleScheduler for how to choose between the global, region, entity and async scopes
 */
public abstract class FoliaModule extends PaperModule {

    private final FoliaScheduler scheduler;

    protected FoliaModule(JavaPlugin plugin) {
        super(plugin);
        this.scheduler = new FoliaScheduler(plugin);
    }

    /**
     * Region-aware scheduling scoped to this module. Every task started through it is
     * cancelled by {@link #onDisable()}, so a module that is disabled and re-enabled
     * does not leave its old tasks running.
     */
    protected ModuleScheduler scheduler() {
        return scheduler;
    }

    /**
     * Cancels this module's tracked tasks, then unregisters its tracked listeners.
     * Call {@code super.onDisable()} if you override this — otherwise both leak.
     */
    @Override
    public void onDisable() {
        scheduler.cancelAll();
        super.onDisable();
    }
}
