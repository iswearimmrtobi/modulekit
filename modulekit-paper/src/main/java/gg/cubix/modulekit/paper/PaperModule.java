package gg.cubix.modulekit.paper;

import gg.cubix.modulekit.api.lifecycle.LoadContext;
import gg.cubix.modulekit.api.module.Module;

import java.util.List;

/**
 * Base class for all Paper platform modules.
 *
 * Declares the three-phase Paper lifecycle. Module authors extend this class
 * and implement the three methods.
 *
 * <pre>
 *   public final class GamemodeModule extends PaperModule {
 *
 *       public static ModuleDescriptor descriptor() { ... }
 *
 *       {@literal @}Override public ModuleDescriptor descriptor() { return GamemodeModule.descriptor(); }
 *       {@literal @}Override public void onLoad(LoadContext ctx)  { ... }
 *       {@literal @}Override public void onEnable()               { ... }
 *       {@literal @}Override public void onDisable()              { ... }
 *   }
 * </pre>
 */
public abstract class PaperModule extends Module<PaperModuleState> {

    /**
     * Called during the Paper plugin's onLoad phase.
     * Register services into the container here via {@link LoadContext#register}.
     * Do NOT register Bukkit event listeners here.
     */
    public abstract void onLoad(LoadContext ctx);

    /**
     * Called during the Paper plugin's onEnable phase.
     * Register Bukkit event listeners here.
     */
    public abstract void onEnable();

    /**
     * Called when the module is disabled or the plugin shuts down.
     * Clean up resources.
     */
    public abstract void onDisable();

    /**
     * Called on a live reload. Default: disable → load → enable.
     * Override for more targeted behaviour (e.g. config-only reload).
     */
    public void onReload(LoadContext ctx) {
        onDisable();
        onLoad(ctx);
        onEnable();
    }

    public List<String> diagnose() {
        return List.of();
    }
}
