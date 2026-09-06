package gg.cubix.modulekit.folia;

import gg.cubix.modulekit.api.module.ModuleDescriptor;
import gg.cubix.modulekit.paper.CommandRegistration;
import gg.cubix.modulekit.paper.PaperModuleManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoliaModuleManagerTest {

    /** {@code externalServices()} is protected on ModuleManager — only a subclass can read it. */
    static class ProbeManager extends FoliaModuleManager {
        ProbeManager(JavaPlugin plugin) {
            super(plugin);
        }

        Set<Class<?>> services() {
            return externalServices();
        }
    }

    static class ProbeModule extends FoliaModule {
        ProbeModule(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public ModuleDescriptor descriptor() {
            return new ModuleDescriptor("probe", "Probe");
        }

        ModuleScheduler exposedScheduler() {
            return scheduler();
        }
    }

    private JavaPlugin mockPlugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder())
            .thenReturn(new File(System.getProperty("java.io.tmpdir"), "modulekit-folia-test"));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("modulekit-folia-test"));
        return plugin;
    }

    @Test
    void isUsableWhereverAPaperModuleManagerIs() {
        assertInstanceOf(PaperModuleManager.class, new FoliaModuleManager(mockPlugin()));
    }

    @Test
    void scheduler_isAvailableAndStable() {
        FoliaModuleManager manager = new FoliaModuleManager(mockPlugin());

        assertNotNull(manager.scheduler());
        assertSame(manager.scheduler(), manager.scheduler());
    }

    @Test
    void registersModuleScheduler_soRequiresResolves() {
        // externalServices() is what DependencyGraph consults to satisfy a module's
        // `requires`, so ModuleScheduler appearing here is what makes it injectable.
        ProbeManager manager = new ProbeManager(mockPlugin());

        assertTrue(manager.services().contains(ModuleScheduler.class));
        assertTrue(manager.services().contains(JavaPlugin.class), "inherited from the Paper adapter");
    }

    @Test
    void pluginScopedScheduler_isSeparateFromEachModulesOwn() {
        JavaPlugin plugin = mockPlugin();
        FoliaModuleManager manager = new FoliaModuleManager(plugin);
        ProbeModule module = new ProbeModule(plugin);

        // Disabling one module must not cancel tasks belonging to the plugin or to a sibling.
        assertNotSame(manager.scheduler(), module.exposedScheduler());
    }

    @Test
    void runDisable_cancelsPluginScopedTasks() {
        JavaPlugin plugin = mockPlugin();
        FoliaModuleManager manager = new FoliaModuleManager(plugin);

        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        ScheduledTask scheduled = mock(ScheduledTask.class);
        when(scheduled.getExecutionState()).thenReturn(ScheduledTask.ExecutionState.IDLE);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(globalScheduler);
            when(globalScheduler.runAtFixedRate(eq(plugin), any(), eq(0L), eq(100L))).thenReturn(scheduled);

            manager.scheduler().global().runRepeating(() -> {}, 0L, 100L);
            assertEquals(1, manager.scheduler().trackedTaskCount());

            manager.runDisable();
        }

        verify(scheduled).cancel();
        assertEquals(0, manager.scheduler().trackedTaskCount());
    }

    @Test
    void inheritsPaperCommandRegistration() {
        JavaPlugin plugin = mockPlugin();
        FoliaModuleManager manager = new FoliaModuleManager(plugin);
        BasicCommand raw = mock(BasicCommand.class);

        manager.addModule(new ProbeModule(plugin) {
            @Override
            public List<CommandRegistration> commands() {
                return List.of(new CommandRegistration("bypass", "desc", List.of(), raw, true));
            }
        });

        Commands registrar = mock(Commands.class);
        manager.registerCommands(registrar, (source, id) -> {});

        verify(registrar).register(eq("bypass"), eq("desc"), anyCollection(), same(raw));
    }
}
