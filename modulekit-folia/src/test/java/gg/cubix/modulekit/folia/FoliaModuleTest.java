package gg.cubix.modulekit.folia;

import gg.cubix.modulekit.api.module.ModuleDescriptor;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoliaModuleTest {

    static class TestModule extends FoliaModule {
        final List<String> calls = new ArrayList<>();

        TestModule(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public ModuleDescriptor descriptor() {
            return new ModuleDescriptor("test", "Test");
        }

        ModuleScheduler exposedScheduler() {
            return scheduler();
        }

        /** {@code registerListener} is protected on PaperModule — reachable only from a subclass. */
        void registerTestListener(Listener listener) {
            registerListener(listener);
        }
    }

    /** A subclass that overrides onDisable and honours the super call, as the docs require. */
    static class OverridingModule extends TestModule {
        OverridingModule(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void onDisable() {
            super.onDisable();
            calls.add("onDisable");
        }
    }

    private JavaPlugin mockPlugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("modulekit-folia-test"));
        return plugin;
    }

    @Test
    void scheduler_isAvailable_andStableAcrossCalls() {
        TestModule module = new TestModule(mockPlugin());

        ModuleScheduler first = module.exposedScheduler();
        assertNotNull(first);
        assertSame(first, module.exposedScheduler());
    }

    @Test
    void eachModuleGetsItsOwnScheduler() {
        JavaPlugin plugin = mockPlugin();

        assertNotSame(
            new TestModule(plugin).exposedScheduler(),
            new TestModule(plugin).exposedScheduler(),
            "task tracking is per module, so disabling one must not cancel another's tasks");
    }

    @Test
    void onDisable_cancelsTrackedTasks() {
        JavaPlugin plugin = mockPlugin();
        TestModule module = new TestModule(plugin);

        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        ScheduledTask scheduled = mock(ScheduledTask.class);
        when(scheduled.getExecutionState()).thenReturn(ScheduledTask.ExecutionState.IDLE);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(globalScheduler);
            when(globalScheduler.runAtFixedRate(eq(plugin), any(), eq(0L), eq(20L))).thenReturn(scheduled);

            module.exposedScheduler().global().runRepeating(() -> {}, 0L, 20L);
            module.onDisable();
        }

        verify(scheduled).cancel();
        assertEquals(0, module.exposedScheduler().trackedTaskCount());
    }

    @Test
    void onDisable_alsoUnregistersListeners_soBothCleanupsSurvive() {
        JavaPlugin plugin = mockPlugin();
        OverridingModule module = new OverridingModule(plugin);
        Listener listener = mock(Listener.class);

        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        ScheduledTask scheduled = mock(ScheduledTask.class);
        when(scheduled.getExecutionState()).thenReturn(ScheduledTask.ExecutionState.IDLE);
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(globalScheduler);
            when(globalScheduler.run(eq(plugin), any())).thenReturn(scheduled);

            module.registerTestListener(listener);
            module.exposedScheduler().global().runNow(() -> {});

            module.onDisable();

            handlers.verify(() -> HandlerList.unregisterAll(listener));
        }

        verify(scheduled).cancel();
        assertEquals(List.of("onDisable"), module.calls, "the subclass override still runs");
    }
}
