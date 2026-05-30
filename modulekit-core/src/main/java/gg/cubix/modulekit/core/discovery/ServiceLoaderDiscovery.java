package gg.cubix.modulekit.core.discovery;

import gg.cubix.modulekit.api.module.Module;
import gg.cubix.modulekit.api.module.ModuleDescriptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Logger;

public final class ServiceLoaderDiscovery {

    private ServiceLoaderDiscovery() {}

    public record DiscoveredModule(ModuleDescriptor descriptor, Class<? extends Module> moduleClass, String faultReason) {
        public boolean isFaulty() { return faultReason != null; }
    }

    public static List<DiscoveredModule> discover(ClassLoader classLoader, Logger logger) {
        List<DiscoveredModule> results = new ArrayList<>();
        ServiceLoader<Module> loader = ServiceLoader.load(Module.class, classLoader);

        for (ServiceLoader.Provider<Module> provider : loader.stream().toList()) {
            Class<? extends Module> type = provider.type();
            try {
                ModuleDescriptor descriptor = resolveDescriptor(type, logger);
                if (descriptor == null) {
                    results.add(new DiscoveredModule(
                        new ModuleDescriptor("unknown-" + type.getSimpleName(), type.getSimpleName()),
                        type,
                        "no descriptor accessor found on " + type.getName()));
                } else {
                    results.add(new DiscoveredModule(descriptor, type, null));
                }
            } catch (ServiceConfigurationError | Exception e) {
                logger.warning("[modulekit] Failed to load module " + type.getName() + ": " + e.getMessage());
                results.add(new DiscoveredModule(
                    new ModuleDescriptor("fault-" + type.getSimpleName(), type.getSimpleName()),
                    type,
                    e.getMessage()));
            }
        }
        return results;
    }

    public static ModuleDescriptor resolveDescriptor(Class<?> moduleClass, Logger logger) {
        // 1. Prefer static descriptor() method — no instantiation required
        try {
            Method staticMethod = moduleClass.getMethod("descriptor");
            if (java.lang.reflect.Modifier.isStatic(staticMethod.getModifiers())
                && ModuleDescriptor.class.isAssignableFrom(staticMethod.getReturnType())) {
                return (ModuleDescriptor) staticMethod.invoke(null);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            logger.warning("[modulekit] Static descriptor() failed on " + moduleClass.getName() + ": " + e.getMessage());
        }

        // 2. Fall back to no-arg constructor for a temporary descriptor-only instance
        try {
            Module temp = (Module) moduleClass.getDeclaredConstructor().newInstance();
            return temp.descriptor();
        } catch (Exception e) {
            logger.warning("[modulekit] No-arg descriptor() failed on " + moduleClass.getName() + ": " + e.getMessage());
        }

        return null;
    }
}
