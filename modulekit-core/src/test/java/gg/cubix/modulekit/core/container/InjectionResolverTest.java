package gg.cubix.modulekit.core.container;

import gg.cubix.modulekit.api.module.Module;
import gg.cubix.modulekit.api.module.ModuleDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InjectionResolverTest {

    enum TestState { ACTIVE, INACTIVE }

    interface ServiceA {}
    interface ServiceB {}
    static class ImplA implements ServiceA {}
    static class ImplB implements ServiceB {}

    static class NoDepModule extends Module<TestState> {
        @Override public ModuleDescriptor descriptor() { return new ModuleDescriptor("no-dep", "NoDep"); }
    }

    static class SingleDepModule extends Module<TestState> {
        final ServiceA serviceA;
        SingleDepModule(ServiceA serviceA) { this.serviceA = serviceA; }
        @Override public ModuleDescriptor descriptor() {
            return ModuleDescriptor.builder("single-dep", "SingleDep").requires(ServiceA.class).build();
        }
    }

    static class MultiDepModule extends Module<TestState> {
        final ServiceA serviceA;
        final ServiceB serviceB;
        MultiDepModule(ServiceA serviceA, ServiceB serviceB) {
            this.serviceA = serviceA;
            this.serviceB = serviceB;
        }
        @Override public ModuleDescriptor descriptor() {
            return ModuleDescriptor.builder("multi-dep", "MultiDep")
                .requires(ServiceA.class, ServiceB.class).build();
        }
    }

    // Two constructors with the same type-set but different parameter order → ambiguous
    static class AmbiguousModule extends Module<TestState> {
        AmbiguousModule(ServiceA a, ServiceB b) {}
        AmbiguousModule(ServiceB b, ServiceA a) {}
        @Override public ModuleDescriptor descriptor() {
            return ModuleDescriptor.builder("ambiguous", "Ambiguous")
                .requires(ServiceA.class, ServiceB.class).build();
        }
    }

    @Test
    void noDepModule_createsInstanceViaNoArgCtor() {
        var result = InjectionResolver.resolve(
            new ModuleDescriptor("no-dep", "NoDep"), NoDepModule.class, Map.of());
        assertFalse(result.isFaulty(), result.faultReason());
        assertInstanceOf(NoDepModule.class, result.instance());
    }

    @Test
    void singleDep_injectsFromRegistry() {
        ServiceA svc = new ImplA();
        var result = InjectionResolver.resolve(
            ModuleDescriptor.builder("single-dep", "SingleDep").requires(ServiceA.class).build(),
            SingleDepModule.class,
            Map.of(ServiceA.class, svc));
        assertFalse(result.isFaulty(), result.faultReason());
        assertSame(svc, ((SingleDepModule) result.instance()).serviceA);
    }

    @Test
    void multipleDeps_injectsAll() {
        ServiceA a = new ImplA();
        ServiceB b = new ImplB();
        var result = InjectionResolver.resolve(
            ModuleDescriptor.builder("multi-dep", "MultiDep").requires(ServiceA.class, ServiceB.class).build(),
            MultiDepModule.class,
            Map.of(ServiceA.class, a, ServiceB.class, b));
        assertFalse(result.isFaulty(), result.faultReason());
        MultiDepModule m = (MultiDepModule) result.instance();
        assertSame(a, m.serviceA);
        assertSame(b, m.serviceB);
    }

    @Test
    void missingService_returnsFault() {
        var result = InjectionResolver.resolve(
            ModuleDescriptor.builder("single-dep", "SingleDep").requires(ServiceA.class).build(),
            SingleDepModule.class,
            Map.of());
        assertTrue(result.isFaulty());
        assertTrue(result.faultReason().contains("ServiceA"));
    }

    @Test
    void ambiguousCtor_returnsFault() {
        var result = InjectionResolver.resolve(
            ModuleDescriptor.builder("ambiguous", "Ambiguous")
                .requires(ServiceA.class, ServiceB.class).build(),
            AmbiguousModule.class,
            Map.of(ServiceA.class, new ImplA(), ServiceB.class, new ImplB()));
        assertTrue(result.isFaulty());
        assertTrue(result.faultReason().contains("ambiguous"));
    }

    @Test
    void noDepModule_emptyRegistryDoesNotNpe() {
        // Regression: old code called existingInstance.getClass() on a null instance. Taking
        // Class<> directly eliminates that null dereference entirely.
        assertDoesNotThrow(() ->
            InjectionResolver.resolve(new ModuleDescriptor("no-dep", "NoDep"), NoDepModule.class, Map.of()));
    }
}
