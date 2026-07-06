package gg.cubix.modulekit.paper;

import io.papermc.paper.command.brigadier.BasicCommand;

import java.util.Collection;

public record CommandRegistration(
        String name,
        String description,
        Collection<String> aliases,
        BasicCommand command,
        boolean bypassModuleGuard
) {
    public CommandRegistration(String name, String description, Collection<String> aliases, BasicCommand command) {
        this(name, description, aliases, command, false);
    }
}
