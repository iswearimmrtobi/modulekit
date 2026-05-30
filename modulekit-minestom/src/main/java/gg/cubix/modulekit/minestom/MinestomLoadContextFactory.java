package gg.cubix.modulekit.minestom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class MinestomLoadContextFactory {

    private final Path rootDataDirectory;
    private final Logger rootLogger;
    private final Map<Class<?>, Object> serviceRegistry = new HashMap<>();

    public MinestomLoadContextFactory(Path rootDataDirectory, Logger rootLogger) {
        this.rootDataDirectory = rootDataDirectory;
        this.rootLogger = rootLogger;
    }

    public MinestomLoadContext create(String moduleId) {
        Path moduleDir = rootDataDirectory.resolve(moduleId);
        try {
            Files.createDirectories(moduleDir);
        } catch (IOException e) {
            rootLogger.warning("[modulekit] Failed to create data folder for '" + moduleId + "': " + e.getMessage());
        }
        return new MinestomLoadContext(
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
