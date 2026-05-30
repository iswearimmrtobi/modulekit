package gg.cubix.modulekit.api.module;

public abstract class Module<S extends Enum<S>> {

    private S state;

    public abstract ModuleDescriptor descriptor();

    public S state() {
        return state;
    }

    public void setState(S state) {
        this.state = state;
    }
}
