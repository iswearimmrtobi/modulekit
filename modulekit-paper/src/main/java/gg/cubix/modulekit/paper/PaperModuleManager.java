package gg.cubix.modulekit.paper;

import gg.cubix.modulekit.core.container.InjectionResolver;
import gg.cubix.modulekit.core.container.ModuleManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ModuleManager specialised for Paper plugins.
 *
 * Handles the full Paper lifecycle automatically.
 * The consuming plugin only needs to call three methods:
 *
 * <pre>
 *   public final class MyPlugin extends JavaPlugin {
 *
 *       private PaperModuleManager modules;
 *
 *       {@literal @}Override
 *       public void onLoad() {
 *           modules = new PaperModuleManager(this);
 *           modules.discover(getClass().getClassLoader());
 *           LoadResult result = modules.runLoad();
 *           if (!result.isClean()) { ... }
 *       }
 *
 *       {@literal @}Override
 *       public void onEnable() {
 *           modules.runEnable();
 *       }
 *
 *       {@literal @}Override
 *       public void onDisable() {
 *           modules.runDisable();
 *       }
 *   }
 * </pre>
 */
public class PaperModuleManager extends ModuleManager<PaperModule, PaperModuleState> {

    private final PaperLoadContextFactory contextFactory;
    private final Map<String, String> faultReasons = new LinkedHashMap<>();

    public PaperModuleManager(JavaPlugin plugin) {
        super(PaperModuleState.DISABLED, PaperModuleState.FAULTY);
        this.contextFactory = new PaperLoadContextFactory(
            plugin.getDataFolder().toPath(),
            plugin.getLogger()
        );
        this.logger = contextFactory.logger();
        contextFactory.registry().put(JavaPlugin.class, plugin);
    }

    public LoadResult runLoad() {
        List<String> loaded = new ArrayList<>();
        List<LoadResult.FaultEntry> faulted = new ArrayList<>();

        runAction(ctx -> {
            String id = ctx.descriptor().id();

            if (ctx.state() == PaperModuleState.FAULTY) {
                String reason = faultReasons.getOrDefault(id, "marked faulty before load phase");
                faulted.add(new LoadResult.FaultEntry(id, reason));
                return;
            }

            if (ctx.module() == null) {
                InjectionResolver.InjectionResult injection = InjectionResolver.resolve(
                    ctx.descriptor(),
                    ctx.moduleClass(),
                    contextFactory.registry()
                );
                if (injection.isFaulty()) {
                    setFaulty(ctx, id, injection.faultReason());
                    faulted.add(new LoadResult.FaultEntry(id, injection.faultReason()));
                    return;
                }
                ctx.setModule((PaperModule) injection.instance());
            }

            PaperLoadContext loadCtx = contextFactory.create(id);
            ctx.module().onLoad(loadCtx);
            loadCtx.closeLoadPhase();

            if (loadCtx.isMarkedFaulty()) {
                setFaulty(ctx, id, loadCtx.faultReason());
                faulted.add(new LoadResult.FaultEntry(id, loadCtx.faultReason()));
                return;
            }

            ctx.setState(PaperModuleState.LOADED);
            loaded.add(id);
        });

        logger.info("[ModuleKit] Load phase complete: " + loaded.size() + " loaded, " + faulted.size() + " faulty");
        for (LoadResult.FaultEntry entry : faulted) {
            logger.warning("[ModuleKit]   FAULTY " + entry.moduleId() + " — " + entry.reason());
        }

        return new LoadResult(List.copyOf(loaded), List.copyOf(faulted));
    }

    public void runEnable() {
        List<String> enabled = new ArrayList<>();

        runAction(ctx -> {
            if (ctx.state() == PaperModuleState.LOADED) {
                ctx.module().onEnable();
                ctx.setState(PaperModuleState.ENABLED);
                enabled.add(ctx.descriptor().id());
            }
        });

        logger.info("[ModuleKit] Enable phase complete: " + enabled.size() + " enabled");
    }

    public void runDisable() {
        runActionReversed(ctx -> {
            if (ctx.state() == PaperModuleState.ENABLED) {
                ctx.module().onDisable();
                ctx.setState(PaperModuleState.DISABLED);
            }
        });
    }

    /** Pre-register a service so modules can declare {@code requires(type)} for it. */
    public <T> void registerService(Class<T> type, T instance) {
        contextFactory.registry().put(type, instance);
    }

    @Override
    protected java.util.Set<Class<?>> externalServices() {
        return contextFactory.registry().keySet();
    }

    /**
     * Re-loads a single DISABLED module: runs injection → onLoad → onEnable.
     * Returns true if the module is now ENABLED. Intended for runtime enable/disable.
     */
    public boolean loadAndEnableModule(String id) {
        var opt = contexts().stream().filter(c -> c.descriptor().id().equals(id)).findFirst();
        if (opt.isEmpty()) return false;
        var ctx = opt.get();

        if (ctx.state() != PaperModuleState.DISABLED) return false;

        if (ctx.module() == null) {
            InjectionResolver.InjectionResult injection = InjectionResolver.resolve(
                ctx.descriptor(), ctx.moduleClass(), contextFactory.registry());
            if (injection.isFaulty()) {
                setFaulty(ctx, id, injection.faultReason());
                return false;
            }
            ctx.setModule((PaperModule) injection.instance());
        }

        PaperLoadContext loadCtx = contextFactory.create(id);
        ctx.module().onLoad(loadCtx);
        loadCtx.closeLoadPhase();

        if (loadCtx.isMarkedFaulty()) {
            setFaulty(ctx, id, loadCtx.faultReason());
            return false;
        }

        ctx.setState(PaperModuleState.LOADED);
        ctx.module().onEnable();
        ctx.setState(PaperModuleState.ENABLED);
        faultReasons.remove(id);
        return true;
    }

    /** Disables a single ENABLED module: runs onDisable and sets state to DISABLED. */
    public boolean disableModule(String id) {
        var opt = contexts().stream().filter(c -> c.descriptor().id().equals(id)).findFirst();
        if (opt.isEmpty()) return false;
        var ctx = opt.get();

        if (ctx.state() != PaperModuleState.ENABLED) return false;

        ctx.module().onDisable();
        ctx.setState(PaperModuleState.DISABLED);
        return true;
    }

    /** Marks a module as FAULTY with a reason, without running any lifecycle. */
    public void markFaulty(String id, String reason) {
        contexts().stream()
            .filter(c -> c.descriptor().id().equals(id))
            .findFirst()
            .ifPresent(ctx -> setFaulty(ctx, id, reason));
    }

    public Optional<String> faultReason(String id) {
        return Optional.ofNullable(faultReasons.get(id));
    }

    private void setFaulty(gg.cubix.modulekit.core.container.ModuleContext<PaperModule, PaperModuleState> ctx,
                           String id, String reason) {
        ctx.setState(PaperModuleState.FAULTY);
        faultReasons.put(id, reason != null ? reason : "unknown");
        logger.warning("[ModuleKit] Module '" + id + "' marked faulty: " + reason);
    }
}
