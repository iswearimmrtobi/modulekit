package gg.cubix.modulekit.paper;

import gg.cubix.modulekit.core.container.InjectionResolver;
import gg.cubix.modulekit.core.container.ModuleManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

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

    public PaperModuleManager(JavaPlugin plugin) {
        super(PaperModuleState.DISABLED, PaperModuleState.FAULTY);
        this.contextFactory = new PaperLoadContextFactory(
            plugin.getDataFolder().toPath(),
            plugin.getLogger()
        );
        this.logger = contextFactory.logger();
    }

    public LoadResult runLoad() {
        List<String> loaded = new ArrayList<>();
        List<LoadResult.FaultEntry> faulted = new ArrayList<>();

        runAction(ctx -> {
            String id = ctx.descriptor().id();

            if (ctx.state() == PaperModuleState.FAULTY) {
                faulted.add(new LoadResult.FaultEntry(id, "marked faulty before load phase"));
                return;
            }

            // Skip injection for modules already instantiated via addModule()
            if (ctx.module() == null) {
                InjectionResolver.InjectionResult injection = InjectionResolver.resolve(
                    ctx.descriptor(),
                    ctx.moduleClass(),
                    contextFactory.registry()
                );
                if (injection.isFaulty()) {
                    ctx.setState(PaperModuleState.FAULTY);
                    logger.warning("[ModuleKit] Module '" + id + "' injection failed: " + injection.faultReason());
                    faulted.add(new LoadResult.FaultEntry(id, injection.faultReason()));
                    return;
                }
                ctx.setModule((PaperModule) injection.instance());
            }

            PaperLoadContext loadCtx = contextFactory.create(id);
            ctx.module().onLoad(loadCtx);
            loadCtx.closeLoadPhase();

            if (loadCtx.isMarkedFaulty()) {
                ctx.setState(PaperModuleState.FAULTY);
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
}
