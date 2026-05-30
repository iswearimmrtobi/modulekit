package gg.cubix.modulekit.minestom;

import gg.cubix.modulekit.api.lifecycle.LoadContext;
import gg.cubix.modulekit.api.module.Module;

import java.util.List;

/**
 * Base class for all Minestom platform modules.
 * Declares Minestom's two-phase lifecycle.
 */
public abstract class MinestomModule extends Module<MinestomModuleState> {

    /**
     * Called during extension initialization.
     * Register services and event listeners here.
     */
    public abstract void onInitialize(LoadContext ctx);

    /**
     * Called during extension termination.
     * Clean up resources.
     */
    public abstract void onTerminate();

    public List<String> diagnose() {
        return List.of();
    }
}
