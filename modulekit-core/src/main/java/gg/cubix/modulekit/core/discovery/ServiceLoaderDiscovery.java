package gg.cubix.modulekit.core.discovery;

import gg.cubix.modulekit.api.module.Module;
import gg.cubix.modulekit.api.module.ModuleDescriptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;

public final class ServiceLoaderDiscovery {

    private static final String SERVICE_FILE = "META-INF/services/" + Module.class.getName();

    private ServiceLoaderDiscovery() {}

    public record DiscoveredModule(ModuleDescriptor descriptor, Class<? extends Module> moduleClass, String faultReason) {
        public boolean isFaulty() { return faultReason != null; }
    }

    /**
     * Discovers module classes by reading META-INF/services files directly.
     * Does NOT use ServiceLoader — avoids the public no-arg constructor requirement
     * that ServiceLoader enforces on classpath services.
     */
    public static List<DiscoveredModule> discover(ClassLoader classLoader, Logger logger) {
        List<DiscoveredModule> results = new ArrayList<>();

        Enumeration<URL> urls;
        try {
            urls = classLoader.getResources(SERVICE_FILE);
        } catch (IOException e) {
            logger.warning("[modulekit] Failed to enumerate service files: " + e.getMessage());
            return results;
        }

        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = stripComment(line).trim();
                    if (line.isEmpty()) continue;
                    discoverClass(line, classLoader, logger, results);
                }
            } catch (IOException e) {
                logger.warning("[modulekit] Failed to read service file " + url + ": " + e.getMessage());
            }
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private static void discoverClass(String className, ClassLoader classLoader, Logger logger,
                                      List<DiscoveredModule> results) {
        Class<? extends Module> type;
        try {
            Class<?> raw = classLoader.loadClass(className);
            if (!Module.class.isAssignableFrom(raw)) {
                logger.warning("[modulekit] " + className + " is not a Module subclass — skipped");
                return;
            }
            type = (Class<? extends Module>) raw;
        } catch (ClassNotFoundException e) {
            logger.warning("[modulekit] Class not found: " + className);
            return;
        } catch (Exception e) {
            logger.warning("[modulekit] Failed to load " + className + ": " + e.getMessage());
            return;
        }

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
        } catch (Exception e) {
            logger.warning("[modulekit] Failed to resolve descriptor for " + className + ": " + e.getMessage());
            results.add(new DiscoveredModule(
                new ModuleDescriptor("fault-" + type.getSimpleName(), type.getSimpleName()),
                type,
                e.getMessage()));
        }
    }

    public static ModuleDescriptor resolveDescriptor(Class<?> moduleClass, Logger logger) {
        // 1. Prefer static getDescriptor() method — no instantiation required.
        //    (Static descriptor() conflicts with the abstract instance descriptor() in Module,
        //    so the static accessor uses a distinct name.)
        try {
            Method staticMethod = moduleClass.getMethod("getDescriptor");
            if (java.lang.reflect.Modifier.isStatic(staticMethod.getModifiers())
                && ModuleDescriptor.class.isAssignableFrom(staticMethod.getReturnType())) {
                return (ModuleDescriptor) staticMethod.invoke(null);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            logger.warning("[modulekit] Static getDescriptor() failed on " + moduleClass.getName() + ": " + e.getMessage());
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

    private static String stripComment(String line) {
        int idx = line.indexOf('#');
        return idx >= 0 ? line.substring(0, idx) : line;
    }
}
