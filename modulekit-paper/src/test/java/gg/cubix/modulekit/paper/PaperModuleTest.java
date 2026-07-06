package gg.cubix.modulekit.paper;

import gg.cubix.modulekit.api.lifecycle.LoadContext;
import gg.cubix.modulekit.api.module.ModuleDescriptor;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class PaperModuleTest {

    static class RecordingModule extends PaperModule {
        final List<String> calls = new ArrayList<>();

        RecordingModule(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public ModuleDescriptor descriptor() {
            return new ModuleDescriptor("recording", "Recording");
        }

        @Override
        protected void onLoad() {
            calls.add("onLoad");
        }

        @Override
        public void onEnable() {
            calls.add("onEnable");
        }

        @Override
        public void onDisable() {
            super.onDisable();
            calls.add("onDisable");
        }
    }

    private JavaPlugin mockPlugin() {
        return mock(JavaPlugin.class);
    }

    @Test
    void onLoad_ctx_delegatesToNoArgOnLoad() {
        RecordingModule module = new RecordingModule(mockPlugin());

        module.onLoad(mock(LoadContext.class));

        assertEquals(List.of("onLoad"), module.calls);
    }

    @Test
    void register_delegatesToLoadContext_duringOnLoad() {
        LoadContext ctx = mock(LoadContext.class);
        RecordingModule module = new RecordingModule(mockPlugin()) {
            @Override
            protected void onLoad() {
                register(String.class, "hello");
            }
        };

        module.onLoad(ctx);

        verify(ctx).register(String.class, "hello");
    }

    @Test
    void markFaulty_delegatesToLoadContext_duringOnLoad() {
        LoadContext ctx = mock(LoadContext.class);
        RecordingModule module = new RecordingModule(mockPlugin()) {
            @Override
            protected void onLoad() {
                markFaulty("boom");
            }
        };

        module.onLoad(ctx);

        verify(ctx).markFaulty("boom");
    }

    @Test
    void onReload_ctx_delegatesToNoArgOnReload_defaultOrderIsDisableLoadEnable() {
        RecordingModule module = new RecordingModule(mockPlugin());

        module.onReload(mock(LoadContext.class));

        assertEquals(List.of("onDisable", "onLoad", "onEnable"), module.calls);
    }

    @Test
    void registerListener_registersWithPluginManager_andAutoUnregistersOnDisable() {
        JavaPlugin plugin = mockPlugin();
        Listener listener = mock(Listener.class);
        PluginManager pluginManager = mock(PluginManager.class);

        RecordingModule module = new RecordingModule(plugin) {
            @Override
            public void onEnable() {
                registerListener(listener);
            }
        };

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<HandlerList> handlerList = mockStatic(HandlerList.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            module.onEnable();
            verify(pluginManager).registerEvents(listener, plugin);

            module.onDisable();
            handlerList.verify(() -> HandlerList.unregisterAll(listener));
        }
    }

    @Test
    void diagnose_defaultsToEmptyList() {
        assertEquals(List.of(), new RecordingModule(mockPlugin()).diagnose());
    }

    @Test
    void commands_defaultsToEmptyList() {
        assertEquals(List.of(), new RecordingModule(mockPlugin()).commands());
    }
}
