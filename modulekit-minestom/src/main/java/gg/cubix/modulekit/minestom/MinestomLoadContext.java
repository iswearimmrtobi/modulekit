package gg.cubix.modulekit.minestom;

import gg.cubix.modulekit.api.lifecycle.LoadContext;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class MinestomLoadContext implements LoadContext {

    private final String moduleId;
    private final Path dataFolder;
    private final Logger logger;
    private final BiConsumer<Class<?>, Object> serviceRegistrar;
    private final Consumer<String> faultReporter;
    private boolean loadPhaseActive = true;
    private boolean markedFaulty = false;
    private String faultReason;

    public MinestomLoadContext(
        String moduleId,
        Path dataFolder,
        Logger logger,
        BiConsumer<Class<?>, Object> serviceRegistrar,
        Consumer<String> faultReporter
    ) {
        this.moduleId = moduleId;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.serviceRegistrar = serviceRegistrar;
        this.faultReporter = faultReporter;
    }

    @Override public Logger logger()   { return logger; }
    @Override public Path dataFolder() { return dataFolder; }

    @Override
    public <T> void register(Class<T> type, T instance) {
        if (!loadPhaseActive) {
            logger.warning("[modulekit] register() called outside onInitialize for module " + moduleId + " — ignored");
            return;
        }
        serviceRegistrar.accept(type, instance);
    }

    @Override
    public void markFaulty(String reason) {
        this.markedFaulty = true;
        this.faultReason = reason;
        faultReporter.accept(reason);
    }

    public void closeLoadPhase() { loadPhaseActive = false; }
    public boolean isMarkedFaulty() { return markedFaulty; }
    public String faultReason() { return faultReason; }
}
