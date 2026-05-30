package gg.cubix.modulekit.core.container;

import gg.cubix.modulekit.api.module.Module;
import gg.cubix.modulekit.api.module.ModuleDescriptor;
import gg.cubix.modulekit.core.discovery.ServiceLoaderDiscovery;
import gg.cubix.modulekit.core.graph.DependencyGraph;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ModuleManager<M extends Module<S>, S extends Enum<S>> {

    private final List<ModuleContext<M, S>> contexts = new ArrayList<>();
    protected final S initialState;
    private final S faultyState;
    protected Logger logger = Logger.getLogger("modulekit");

    public record LoadResult(List<String> loaded, List<FaultEntry> faulted) {
        public record FaultEntry(String moduleId, String reason) {}
        public boolean isClean() { return faulted.isEmpty(); }
    }

    public ModuleManager(S initialState, S faultyState) {
        this.initialState = initialState;
        this.faultyState = faultyState;
    }

    public void discover(ClassLoader loader) {
        List<ServiceLoaderDiscovery.DiscoveredModule> found = ServiceLoaderDiscovery.discover(loader, logger);

        List<ModuleDescriptor> descriptors = found.stream()
            .map(ServiceLoaderDiscovery.DiscoveredModule::descriptor)
            .toList();

        DependencyGraph graph = DependencyGraph.build(descriptors);

        // first-wins on duplicate ids, consistent with DependencyGraph behaviour
        Map<String, ServiceLoaderDiscovery.DiscoveredModule> byId = new LinkedHashMap<>();
        for (ServiceLoaderDiscovery.DiscoveredModule dm : found) {
            byId.putIfAbsent(dm.descriptor().id(), dm);
        }

        // loadOrder() includes all ids — topological first, cyclic appended at end
        for (String id : graph.loadOrder()) {
            ServiceLoaderDiscovery.DiscoveredModule dm = byId.get(id);
            if (dm == null) continue;

            @SuppressWarnings("unchecked")
            Class<? extends M> cls = (Class<? extends M>) dm.moduleClass();
            ModuleContext<M, S> ctx = new ModuleContext<>(cls, dm.descriptor(), initialState);

            if (dm.isFaulty()) {
                ctx.setState(faultyState);
                logger.warning("[modulekit] Module '" + id + "' discovery fault: " + dm.faultReason());
            } else if (graph.isFaulty(id)) {
                ctx.setState(faultyState);
                logger.warning("[modulekit] Module '" + id + "' graph fault: "
                    + graph.faultReason(id).orElse("unknown"));
            }

            contexts.add(ctx);
        }
    }

    public void addModule(M module) {
        @SuppressWarnings("unchecked")
        Class<? extends M> cls = (Class<? extends M>) module.getClass();
        ModuleContext<M, S> ctx = new ModuleContext<>(cls, module.descriptor(), initialState);
        ctx.setModule(module);
        contexts.add(ctx);
    }

    public Optional<M> getModule(String id) {
        return contexts.stream()
            .filter(ctx -> ctx.descriptor().id().equals(id))
            .map(ModuleContext::module)
            .filter(Objects::nonNull)
            .findFirst();
    }

    @SuppressWarnings("unchecked")
    public <T extends M> Optional<T> getModule(Class<T> type) {
        return contexts.stream()
            .filter(ctx -> type.isAssignableFrom(ctx.moduleClass()))
            .map(ctx -> (T) ctx.module())
            .filter(Objects::nonNull)
            .findFirst();
    }

    public void runAction(Consumer<ModuleContext<M, S>> action) {
        contexts.forEach(action);
    }

    public void runActionReversed(Consumer<ModuleContext<M, S>> action) {
        List<ModuleContext<M, S>> reversed = new ArrayList<>(contexts);
        Collections.reverse(reversed);
        reversed.forEach(action);
    }

    public List<ModuleContext<M, S>> contexts() {
        return Collections.unmodifiableList(contexts);
    }
}
