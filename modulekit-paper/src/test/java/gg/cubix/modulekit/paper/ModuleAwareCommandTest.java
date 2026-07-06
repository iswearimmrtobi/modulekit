package gg.cubix.modulekit.paper;

import gg.cubix.modulekit.api.module.ModuleDescriptor;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModuleAwareCommandTest {

    private PaperModuleManager managerWithModule(String moduleId, PaperModuleState state) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(new File(System.getProperty("java.io.tmpdir"), "modulekit-test"));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("modulekit-test"));

        PaperModuleManager manager = new PaperModuleManager(plugin);
        PaperModule module = new PaperModule(plugin) {
            @Override
            public ModuleDescriptor descriptor() {
                return new ModuleDescriptor(moduleId, moduleId);
            }
        };
        manager.addModule(module);
        manager.contexts().get(0).setState(state);
        return manager;
    }

    @Test
    void execute_delegates_whenModuleEnabled() {
        PaperModuleManager manager = managerWithModule("mod", PaperModuleState.ENABLED);
        BasicCommand delegate = mock(BasicCommand.class);
        List<String> blocked = new java.util.ArrayList<>();
        ModuleAwareCommand cmd = new ModuleAwareCommand(manager, "mod", delegate,
            (source, moduleId) -> blocked.add(moduleId));

        CommandSourceStack source = mock(CommandSourceStack.class);
        String[] args = {"a"};
        cmd.execute(source, args);

        verify(delegate).execute(source, args);
        assertTrue(blocked.isEmpty());
    }

    @Test
    void execute_invokesOnBlocked_whenModuleNotEnabled() {
        PaperModuleManager manager = managerWithModule("mod", PaperModuleState.DISABLED);
        BasicCommand delegate = mock(BasicCommand.class);
        List<String> blocked = new java.util.ArrayList<>();
        ModuleAwareCommand cmd = new ModuleAwareCommand(manager, "mod", delegate,
            (source, moduleId) -> blocked.add(moduleId));

        CommandSourceStack source = mock(CommandSourceStack.class);
        cmd.execute(source, new String[0]);

        verify(delegate, never()).execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertEquals(List.of("mod"), blocked);
    }

    @Test
    void suggest_returnsEmpty_whenModuleNotEnabled() {
        PaperModuleManager manager = managerWithModule("mod", PaperModuleState.FAULTY);
        BasicCommand delegate = mock(BasicCommand.class);
        ModuleAwareCommand cmd = new ModuleAwareCommand(manager, "mod", delegate, (s, id) -> {});

        assertTrue(cmd.suggest(mock(CommandSourceStack.class), new String[0]).isEmpty());
        verify(delegate, never()).suggest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void suggest_delegates_whenModuleEnabled() {
        PaperModuleManager manager = managerWithModule("mod", PaperModuleState.ENABLED);
        BasicCommand delegate = mock(BasicCommand.class);
        when(delegate.suggest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of("x"));
        ModuleAwareCommand cmd = new ModuleAwareCommand(manager, "mod", delegate, (s, id) -> {});

        assertEquals(List.of("x"), cmd.suggest(mock(CommandSourceStack.class), new String[0]));
    }

    @Test
    void permission_alwaysDelegates_regardlessOfModuleState() {
        PaperModuleManager manager = managerWithModule("mod", PaperModuleState.DISABLED);
        BasicCommand delegate = mock(BasicCommand.class);
        when(delegate.permission()).thenReturn("perm.node");
        ModuleAwareCommand cmd = new ModuleAwareCommand(manager, "mod", delegate, (s, id) -> {});

        assertEquals("perm.node", cmd.permission());
        verify(delegate, times(1)).permission();
    }
}
