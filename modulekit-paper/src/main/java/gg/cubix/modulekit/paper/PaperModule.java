package gg.cubix.modulekit.paper;

import gg.cubix.modulekit.api.lifecycle.LoadContext;
import gg.cubix.modulekit.api.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all Paper platform modules.
 *
 * Declares the three-phase Paper lifecycle and bundles the boilerplate every
 * Paper module needs on top of the raw {@link LoadContext}-based contract:
 * a {@link JavaPlugin} reference, Bukkit listener bookkeeping, and no-arg
 * lifecycle convenience methods.
 *
 * <pre>
 *   public final class GamemodeModule extends PaperModule {
 *
 *       public GamemodeModule(JavaPlugin plugin) { super(plugin); }
 *
 *       public static ModuleDescriptor descriptor() { ... }
 *
 *       {@literal @}Override public ModuleDescriptor descriptor() { return GamemodeModule.descriptor(); }
 *       {@literal @}Override protected void onLoad()   { ... }
 *       {@literal @}Override public void onEnable()    { ... }
 *       {@literal @}Override public void onDisable()   { ... }
 *   }
 * </pre>
 */
public abstract class PaperModule extends Module<PaperModuleState> {

    protected final JavaPlugin plugin;

    private final List<Listener> listeners = new ArrayList<>();
    private LoadContext loadCtx;

    protected PaperModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Final — stashes {@code ctx} and delegates to {@link #onLoad()}. Do not override. */
    public final void onLoad(LoadContext ctx) {
        this.loadCtx = ctx;
        try {
            onLoad();
        } finally {
            this.loadCtx = null;
        }
    }

    /** Final — stashes {@code ctx} and delegates to {@link #onReload()}. Do not override. */
    public final void onReload(LoadContext ctx) {
        this.loadCtx = ctx;
        try {
            onReload();
        } finally {
            this.loadCtx = null;
        }
    }

    /**
     * Called during the Paper plugin's onLoad phase.
     * Use {@link #register(Class, Object)} to register services here.
     * Do NOT register Bukkit event listeners here — use {@link #onEnable()} instead.
     */
    protected void onLoad() {}

    /**
     * Called during the Paper plugin's onEnable phase.
     * Register Bukkit event listeners here via {@link #registerListener(Listener)}.
     */
    public void onEnable() {}

    /**
     * Called on a live reload. Default: disable → load → enable.
     * Override for more targeted behaviour (e.g. config-only reload).
     */
    protected void onReload() {
        onDisable();
        onLoad();
        onEnable();
    }

    /**
     * Every listener registered via {@link #registerListener(Listener)} is unregistered
     * automatically. Call {@code super.onDisable()} if you override this to keep that guarantee.
     */
    public void onDisable() {
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    /** Register a service into the ModuleKit service registry. Only valid during {@link #onLoad()}. */
    protected <T> void register(Class<T> type, T instance) {
        loadCtx.register(type, instance);
    }

    /** Aborts this module's activation. Only valid during {@link #onLoad()}. */
    protected void markFaulty(String reason) {
        loadCtx.markFaulty(reason);
    }

    /** Registers a Bukkit listener against this module's plugin, tracked for auto-unregister on disable. */
    protected void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    /** Commands this module wants registered. Invisible to ModuleKit's core lifecycle. */
    public List<CommandRegistration> commands() {
        return List.of();
    }

    public List<String> diagnose() {
        return List.of();
    }
}
