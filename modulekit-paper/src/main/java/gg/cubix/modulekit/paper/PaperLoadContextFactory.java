package gg.cubix.modulekit.paper;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class PaperLoadContextFactory {

    private final Path rootDataFolder;
    private final Logger rootLogger;
    private final Map<Class<?>, Object> serviceRegistry = new HashMap<>();

    public PaperLoadContextFactory(Path rootDataFolder, Logger rootLogger) {
        this.rootDataFolder = rootDataFolder;
        this.rootLogger = rootLogger;
    }

    public PaperLoadContext create(String moduleId) {
        Path moduleDir = rootDataFolder.resolve(moduleId);
        return new PaperLoadContext(
            moduleId,
            moduleDir,
            Logger.getLogger(rootLogger.getName() + "." + moduleId),
            (type, instance) -> serviceRegistry.put(type, instance),
            reason -> rootLogger.warning("[modulekit] Module '" + moduleId + "' marked faulty: " + reason)
        );
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> service(Class<T> type) {
        return Optional.ofNullable((T) serviceRegistry.get(type));
    }

    public Map<Class<?>, Object> registry() { return serviceRegistry; }

    public Logger logger() { return rootLogger; }
}
