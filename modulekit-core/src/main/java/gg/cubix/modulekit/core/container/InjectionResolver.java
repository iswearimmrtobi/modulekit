package gg.cubix.modulekit.core.container;

import gg.cubix.modulekit.api.module.Module;
import gg.cubix.modulekit.api.module.ModuleDescriptor;

import java.lang.reflect.Constructor;
import java.util.*;

public final class InjectionResolver {

    private InjectionResolver() {}

    public record InjectionResult(Module instance, String faultReason) {
        public boolean isFaulty() { return faultReason != null; }
    }

    public static InjectionResult resolve(
        ModuleDescriptor descriptor,
        Class<? extends Module> moduleClass,
        Map<Class<?>, Object> serviceRegistry
    ) {
        List<Class<?>> required = descriptor.requires();

        if (required.isEmpty()) {
            try {
                Module instance = moduleClass.getDeclaredConstructor().newInstance();
                return new InjectionResult(instance, null);
            } catch (Exception e) {
                return new InjectionResult(null, "failed to instantiate no-arg module: " + e.getMessage());
            }
        }

        Set<Class<?>> requiredSet = new LinkedHashSet<>(required);

        Constructor<?> matched = null;
        for (Constructor<?> ctor : moduleClass.getDeclaredConstructors()) {
            Set<Class<?>> params = new LinkedHashSet<>(Arrays.asList(ctor.getParameterTypes()));
            if (params.equals(requiredSet)) {
                if (matched != null) {
                    return new InjectionResult(null,
                        "ambiguous constructor — declare exactly one constructor matching your requires list");
                }
                matched = ctor;
            }
        }

        if (matched == null) {
            return new InjectionResult(null,
                "no constructor found matching required types: " + requiredSet);
        }

        Object[] args = new Object[matched.getParameterCount()];
        for (int i = 0; i < matched.getParameterTypes().length; i++) {
            Class<?> paramType = matched.getParameterTypes()[i];
            Object service = serviceRegistry.get(paramType);
            if (service == null) {
                return new InjectionResult(null,
                    "service not found in registry for type " + paramType.getName());
            }
            args[i] = service;
        }

        try {
            matched.setAccessible(true);
            Module instance = (Module) matched.newInstance(args);
            return new InjectionResult(instance, null);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return new InjectionResult(null, "constructor threw exception: " + cause.getMessage());
        }
    }
}
