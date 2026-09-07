package gg.cubix.modulekit.folia;

import gg.cubix.modulekit.paper.PaperModuleManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@link PaperModuleManager} for plugins that also run on Folia.
 *
 * The lifecycle is inherited wholesale — {@code discover}, {@code runLoad},
 * {@code runEnable}, {@code registerCommands}, {@code loadAndEnableModule} and
 * {@code disableModule} all behave exactly as they do on Paper, because none of them
 * assume a main thread. Wiring is identical too:
 *
 * <pre>
 *   public final class MyPlugin extends JavaPlugin {
 *
 *       private FoliaModuleManager modules;
 *
 *       {@literal @}Override
 *       public void onLoad() {
 *           modules = new FoliaModuleManager(this);
 *           modules.discover(getClass().getClassLoader());
 *           modules.runLoad();
 *       }
 *
 *       {@literal @}Override public void onEnable()  { modules.runEnable(); }
 *       {@literal @}Override public void onDisable() { modules.runDisable(); }
 *   }
 * </pre>
 *
 * Remember that Folia refuses to load a plugin unless its {@code paper-plugin.yml} (or
 * {@code plugin.yml}) declares {@code folia-supported: true}. That file belongs to your
 * plugin, so ModuleKit cannot add it for you.
 */
public class FoliaModuleManager extends PaperModuleManager {

    private final FoliaScheduler pluginScheduler;

    public FoliaModuleManager(JavaPlugin plugin) {
        super(plugin);
        this.pluginScheduler = new FoliaScheduler(plugin);

        // Makes `requires(ModuleScheduler.class)` resolvable for modules and for plain
        // services, which have no FoliaModule of their own to borrow a scheduler from.
        registerService(ModuleScheduler.class, pluginScheduler);

        logger.info("[ModuleKit] Platform: " + FoliaPlatform.describe());
    }

    /**
     * The plugin-scoped scheduler — the one injected as {@link ModuleScheduler}.
     *
     * Prefer {@code FoliaModule#scheduler()} inside a module: its tasks are cancelled when
     * that single module is disabled, whereas these live until the whole plugin shuts down.
     */
    public ModuleScheduler scheduler() {
        return pluginScheduler;
    }

    /**
     * Disables every enabled module in reverse order, then cancels any plugin-scoped tasks.
     * Module-scoped tasks are already gone by then — {@link FoliaModule#onDisable()} cancels
     * its own.
     */
    @Override
    public void runDisable() {
        super.runDisable();
        pluginScheduler.cancelAll();
    }
}
