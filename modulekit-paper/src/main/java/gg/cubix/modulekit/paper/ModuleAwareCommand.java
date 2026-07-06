package gg.cubix.modulekit.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

public final class ModuleAwareCommand implements BasicCommand {

    private final PaperModuleManager moduleManager;
    private final String moduleName;
    private final BasicCommand delegate;
    private final BiConsumer<CommandSourceStack, String> onBlocked;

    public ModuleAwareCommand(PaperModuleManager moduleManager, String moduleName, BasicCommand delegate,
                               BiConsumer<CommandSourceStack, String> onBlocked) {
        this.moduleManager = moduleManager;
        this.moduleName = moduleName;
        this.delegate = delegate;
        this.onBlocked = onBlocked;
    }

    private boolean isEnabled() {
        return moduleManager.getModule(moduleName)
            .map(m -> m.state() == PaperModuleState.ENABLED)
            .orElse(false);
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String @NotNull [] args) {
        if (!isEnabled()) {
            onBlocked.accept(source, moduleName);
            return;
        }
        delegate.execute(source, args);
    }

    @Override
    public String permission() {
        return delegate.permission();
    }

    @Override
    public @NonNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String @NotNull [] args) {
        if (!isEnabled()) return List.of();
        return delegate.suggest(source, args);
    }
}
