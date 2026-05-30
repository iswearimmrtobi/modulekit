package gg.cubix.modulekit.minestom;

import gg.cubix.modulekit.core.container.InjectionResolver;
import gg.cubix.modulekit.core.container.ModuleManager;
import net.minestom.server.extensions.Extension;

import java.util.ArrayList;
import java.util.List;

public class MinestomModuleManager extends ModuleManager<MinestomModule, MinestomModuleState> {

    private final MinestomLoadContextFactory contextFactory;

    public MinestomModuleManager(Extension extension) {
        super(MinestomModuleState.DISABLED, MinestomModuleState.FAULTY);
        this.contextFactory = new MinestomLoadContextFactory(
            extension.getDataDirectory(),
            extension.getLogger()
        );
        this.logger = contextFactory.logger();
    }

    public LoadResult runInitialize() {
        List<String> initialized = new ArrayList<>();
        List<LoadResult.FaultEntry> faulted = new ArrayList<>();

        runAction(ctx -> {
            String id = ctx.descriptor().id();

            if (ctx.state() == MinestomModuleState.FAULTY) {
                faulted.add(new LoadResult.FaultEntry(id, "marked faulty before initialize phase"));
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
                    ctx.setState(MinestomModuleState.FAULTY);
                    logger.warning("[ModuleKit] Module '" + id + "' injection failed: " + injection.faultReason());
                    faulted.add(new LoadResult.FaultEntry(id, injection.faultReason()));
                    return;
                }
                ctx.setModule((MinestomModule) injection.instance());
            }

            MinestomLoadContext loadCtx = contextFactory.create(id);
            ctx.module().onInitialize(loadCtx);
            loadCtx.closeLoadPhase();

            if (loadCtx.isMarkedFaulty()) {
                ctx.setState(MinestomModuleState.FAULTY);
                faulted.add(new LoadResult.FaultEntry(id, loadCtx.faultReason()));
                return;
            }

            ctx.setState(MinestomModuleState.INITIALIZED);
            initialized.add(id);
        });

        logger.info("[ModuleKit] Initialize phase complete: " + initialized.size() + " initialized, "
            + faulted.size() + " faulty");
        for (LoadResult.FaultEntry entry : faulted) {
            logger.warning("[ModuleKit]   FAULTY " + entry.moduleId() + " — " + entry.reason());
        }

        return new LoadResult(List.copyOf(initialized), List.copyOf(faulted));
    }

    public void runTerminate() {
        runActionReversed(ctx -> {
            if (ctx.state() == MinestomModuleState.INITIALIZED) {
                ctx.module().onTerminate();
                ctx.setState(MinestomModuleState.DISABLED);
            }
        });
    }
}
