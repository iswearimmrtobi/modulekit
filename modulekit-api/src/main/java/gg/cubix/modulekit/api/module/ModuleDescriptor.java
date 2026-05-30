package gg.cubix.modulekit.api.module;

import java.util.List;

public record ModuleDescriptor(
    String id,
    String displayName,
    List<Class<?>> provides,
    List<Class<?>> requires,
    boolean system
) {
    public ModuleDescriptor {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("ModuleDescriptor id must not be null or blank");
        provides = List.copyOf(provides);
        requires = List.copyOf(requires);
    }

    public ModuleDescriptor(String id, String displayName) {
        this(id, displayName, List.of(), List.of(), false);
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private List<Class<?>> provides = List.of();
        private List<Class<?>> requires = List.of();
        private boolean system = false;

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder provides(Class<?>... types) {
            this.provides = List.of(types);
            return this;
        }

        public Builder requires(Class<?>... types) {
            this.requires = List.of(types);
            return this;
        }

        public Builder system() {
            this.system = true;
            return this;
        }

        public ModuleDescriptor build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("ModuleDescriptor id must not be null or blank");
            return new ModuleDescriptor(id, displayName, provides, requires, system);
        }
    }
}
