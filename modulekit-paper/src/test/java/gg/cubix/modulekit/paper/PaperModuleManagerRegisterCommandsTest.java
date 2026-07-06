package gg.cubix.modulekit.paper;

import gg.cubix.modulekit.api.module.ModuleDescriptor;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperModuleManagerRegisterCommandsTest {

    private JavaPlugin mockPlugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(new File(System.getProperty("java.io.tmpdir"), "modulekit-test"));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("modulekit-test"));
        return plugin;
    }

    @Test
    void bypassModuleGuard_registersRawCommand_unwrapped() {
        JavaPlugin plugin = mockPlugin();
        PaperModuleManager manager = new PaperModuleManager(plugin);
        BasicCommand raw = mock(BasicCommand.class);
        CommandRegistration registration = new CommandRegistration("bypass", "desc", List.of(), raw, true);
        manager.addModule(new PaperModule(plugin) {
            @Override public ModuleDescriptor descriptor() { return new ModuleDescriptor("mod", "mod"); }
            @Override public List<CommandRegistration> commands() { return List.of(registration); }
        });

        Commands registrar = mock(Commands.class);
        manager.registerCommands(registrar, (source, moduleId) -> {});

        ArgumentCaptor<BasicCommand> captor = ArgumentCaptor.forClass(BasicCommand.class);
        verify(registrar).register(eq("bypass"), eq("desc"), anyCollection(), captor.capture());
        assertSame(raw, captor.getValue());
    }

    @Test
    void guardedCommand_isWrappedInModuleAwareCommand() {
        JavaPlugin plugin = mockPlugin();
        PaperModuleManager manager = new PaperModuleManager(plugin);
        BasicCommand raw = mock(BasicCommand.class);
        CommandRegistration registration = new CommandRegistration("guarded", "desc", List.of("g"), raw);
        manager.addModule(new PaperModule(plugin) {
            @Override public ModuleDescriptor descriptor() { return new ModuleDescriptor("mod", "mod"); }
            @Override public List<CommandRegistration> commands() { return List.of(registration); }
        });

        Commands registrar = mock(Commands.class);
        manager.registerCommands(registrar, (source, moduleId) -> {});

        ArgumentCaptor<BasicCommand> captor = ArgumentCaptor.forClass(BasicCommand.class);
        verify(registrar).register(eq("guarded"), eq("desc"), eq(List.of("g")), captor.capture());
        assertTrue(captor.getValue() instanceof ModuleAwareCommand);
    }

    @Test
    void noCommands_registersNothing() {
        JavaPlugin plugin = mockPlugin();
        PaperModuleManager manager = new PaperModuleManager(plugin);
        manager.addModule(new PaperModule(plugin) {
            @Override public ModuleDescriptor descriptor() { return new ModuleDescriptor("mod", "mod"); }
        });

        Commands registrar = mock(Commands.class);
        manager.registerCommands(registrar, (source, moduleId) -> {});

        verify(registrar, org.mockito.Mockito.never()).register(any(), any(), anyCollection(), any());
    }
}
