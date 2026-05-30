package gg.cubix.modulekit.core.container;

import gg.cubix.modulekit.api.module.Module;
import gg.cubix.modulekit.api.module.ModuleDescriptor;

public class ModuleContext<M extends Module<S>, S extends Enum<S>> {

    private M module;
    private final Class<? extends M> moduleClass;
    private final ModuleDescriptor descriptor;
    private S state;

    public ModuleContext(Class<? extends M> moduleClass, ModuleDescriptor descriptor, S initialState) {
        this.moduleClass = moduleClass;
        this.descriptor = descriptor;
        this.state = initialState;
    }

    public M module()                        { return module; }
    public Class<? extends M> moduleClass()  { return moduleClass; }
    public ModuleDescriptor descriptor()     { return descriptor; }

    public void setModule(M module) {
        this.module = module;
        module.setState(state);
    }

    public S state() { return state; }

    public void setState(S state) {
        this.state = state;
        if (module != null) module.setState(state);
    }
}
