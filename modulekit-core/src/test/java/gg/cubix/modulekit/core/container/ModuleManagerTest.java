package gg.cubix.modulekit.core.container;

import gg.cubix.modulekit.api.module.Module;
import gg.cubix.modulekit.api.module.ModuleDescriptor;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ModuleManagerTest {

    // ── Shared test infrastructure ────────────────────────────────────────────

    enum State { DISABLED, LOADED, ENABLED, FAULTY }

    interface GreetService {}
    static class GreetServiceImpl implements GreetService {}

    abstract static class TestModule extends Module<State> {
        boolean loadCalled, enableCalled, disableCalled;
    }

    static class SimpleModule extends TestModule {
        @Override public ModuleDescriptor descriptor() { return new ModuleDescriptor("simple", "Simple"); }
    }

    static class ProviderModule extends TestModule {
        @Override public ModuleDescriptor descriptor() {
            return ModuleDescriptor.builder("provider", "Provider").provides(GreetService.class).build();
        }
    }

    static class ConsumerModule extends TestModule {
        final GreetService greeter;
        ConsumerModule(GreetService greeter) { this.greeter = greeter; }
        @Override public ModuleDescriptor descriptor() {
            return ModuleDescriptor.builder("consumer", "Consumer").requires(GreetService.class).build();
        }
    }

    static class TestManager extends ModuleManager<TestModule, State> {
        final Map<Class<?>, Object> registry = new HashMap<>();

        TestManager() { super(State.DISABLED, State.FAULTY); }

        ModuleManager.LoadResult runLoad() {
            List<String> loaded = new ArrayList<>();
            List<ModuleManager.LoadResult.FaultEntry> faulted = new ArrayList<>();
            runAction(ctx -> {
                String id = ctx.descriptor().id();
                if (ctx.state() == State.FAULTY) {
                    faulted.add(new ModuleManager.LoadResult.FaultEntry(id, "pre-faulty"));
                    return;
                }
                if (ctx.module() == null) {
                    InjectionResolver.InjectionResult inj = InjectionResolver.resolve(
                        ctx.descriptor(), ctx.moduleClass(), registry);
                    if (inj.isFaulty()) {
                        ctx.setState(State.FAULTY);
                        faulted.add(new ModuleManager.LoadResult.FaultEntry(id, inj.faultReason()));
                        return;
                    }
                    ctx.setModule((TestModule) inj.instance());
                }
                ctx.module().loadCalled = true;
                ctx.setState(State.LOADED);
                loaded.add(id);
            });
            return new LoadResult(List.copyOf(loaded), List.copyOf(faulted));
        }

        void runEnable() {
            runAction(ctx -> {
                if (ctx.state() == State.LOADED) {
                    ctx.module().enableCalled = true;
                    ctx.setState(State.ENABLED);
                }
            });
        }

        void runDisable() {
            runActionReversed(ctx -> {
                if (ctx.state() == State.ENABLED) {
                    ctx.module().disableCalled = true;
                    ctx.setState(State.DISABLED);
                }
            });
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void happyPath_load_enable_disable() {
        TestManager mgr = new TestManager();
        SimpleModule m = new SimpleModule();
        mgr.addModule(m);

        ModuleManager.LoadResult loadResult = mgr.runLoad();
        assertTrue(loadResult.isClean(), loadResult.faulted().toString());
        assertEquals(List.of("simple"), loadResult.loaded());
        assertEquals(State.LOADED, mgr.contexts().get(0).state());
        assertTrue(m.loadCalled);

        mgr.runEnable();
        assertEquals(State.ENABLED, mgr.contexts().get(0).state());
        assertTrue(m.enableCalled);

        mgr.runDisable();
        assertEquals(State.DISABLED, mgr.contexts().get(0).state());
        assertTrue(m.disableCalled);
    }

    @Test
    void faultyModule_isSkippedInLoad() {
        TestManager mgr = new TestManager();
        SimpleModule m = new SimpleModule();
        mgr.addModule(m);
        mgr.contexts().get(0).setState(State.FAULTY);

        ModuleManager.LoadResult result = mgr.runLoad();

        assertFalse(result.isClean());
        assertEquals(0, result.loaded().size());
        assertEquals(1, result.faulted().size());
        assertEquals("simple", result.faulted().get(0).moduleId());
        assertFalse(m.loadCalled);
    }

    @Test
    void faultyModule_isSkippedInEnable() {
        TestManager mgr = new TestManager();
        SimpleModule m = new SimpleModule();
        mgr.addModule(m);
        mgr.runLoad();

        mgr.contexts().get(0).setState(State.FAULTY);
        mgr.runEnable();

        assertEquals(State.FAULTY, mgr.contexts().get(0).state());
        assertFalse(m.enableCalled);
    }

    @Test
    void disable_happensInReverseLoadOrder() {
        TestManager mgr = new TestManager();
        List<String> order = new ArrayList<>();

        TestModule first = new TestModule() {
            @Override public ModuleDescriptor descriptor() { return new ModuleDescriptor("first", "First"); }
        };
        TestModule second = new TestModule() {
            @Override public ModuleDescriptor descriptor() { return new ModuleDescriptor("second", "Second"); }
        };

        mgr.addModule(first);
        mgr.addModule(second);
        mgr.runLoad();
        mgr.runEnable();

        mgr.runActionReversed(ctx -> {
            if (ctx.state() == State.ENABLED) order.add(ctx.descriptor().id());
        });

        assertEquals(List.of("second", "first"), order);
    }

    @Test
    void injection_consumerReceivesService() {
        TestManager mgr = new TestManager();
        mgr.registry.put(GreetService.class, new GreetServiceImpl());

        InjectionResolver.InjectionResult inj = InjectionResolver.resolve(
            ModuleDescriptor.builder("consumer", "Consumer").requires(GreetService.class).build(),
            ConsumerModule.class,
            mgr.registry);

        assertFalse(inj.isFaulty());
        assertNotNull(((ConsumerModule) inj.instance()).greeter);
    }

    @Test
    void getModule_byId_returnsModule() {
        TestManager mgr = new TestManager();
        SimpleModule m = new SimpleModule();
        mgr.addModule(m);

        assertTrue(mgr.getModule("simple").isPresent());
        assertSame(m, mgr.getModule("simple").get());
    }

    @Test
    void getModule_byId_missingReturnsEmpty() {
        TestManager mgr = new TestManager();
        assertTrue(mgr.getModule("nonexistent").isEmpty());
    }

    @Test
    void getModule_byClass_returnsModule() {
        TestManager mgr = new TestManager();
        SimpleModule m = new SimpleModule();
        mgr.addModule(m);

        assertTrue(mgr.getModule(SimpleModule.class).isPresent());
        assertSame(m, mgr.getModule(SimpleModule.class).get());
    }

    @Test
    void loadResult_isClean_whenNoFaults() {
        TestManager mgr = new TestManager();
        mgr.addModule(new SimpleModule());
        assertTrue(mgr.runLoad().isClean());
    }

    @Test
    void addModule_presetsInstance_skipInjectionInLoad() {
        TestManager mgr = new TestManager();
        SimpleModule m = new SimpleModule();
        mgr.addModule(m);

        mgr.runLoad();

        // The pre-set instance should be the one used — no new instance created by InjectionResolver
        assertSame(m, mgr.contexts().get(0).module());
    }
}
