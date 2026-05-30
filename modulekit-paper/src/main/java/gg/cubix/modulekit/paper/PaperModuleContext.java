package gg.cubix.modulekit.paper;

import gg.cubix.modulekit.api.module.ModuleDescriptor;
import gg.cubix.modulekit.core.container.ModuleContext;

public class PaperModuleContext extends ModuleContext<PaperModule, PaperModuleState> {

    public PaperModuleContext(Class<? extends PaperModule> moduleClass, ModuleDescriptor descriptor) {
        super(moduleClass, descriptor, PaperModuleState.DISABLED);
    }
}
