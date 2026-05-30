package gg.cubix.modulekit.core.graph;

import gg.cubix.modulekit.api.module.ModuleDescriptor;

import java.util.*;

public final class DependencyGraph {

    private final Map<String, ModuleDescriptor> descriptors;
    private final Map<Class<?>, String> serviceProviders;
    private final Map<String, Set<String>> moduleDependencies;
    private final Map<String, Set<String>> moduleDependents;
    private final Map<String, String> faultReasons;
    private final List<String> topologicalOrder;

    private DependencyGraph(
        Map<String, ModuleDescriptor> descriptors,
        Map<Class<?>, String> serviceProviders,
        Map<String, Set<String>> moduleDependencies,
        Map<String, Set<String>> moduleDependents,
        Map<String, String> faultReasons,
        List<String> topologicalOrder
    ) {
        this.descriptors = descriptors;
        this.serviceProviders = serviceProviders;
        this.moduleDependencies = moduleDependencies;
        this.moduleDependents = moduleDependents;
        this.faultReasons = faultReasons;
        this.topologicalOrder = topologicalOrder;
    }

    /**
     * Build a dependency graph.
     *
     * @param descriptorList   all discovered module descriptors
     * @param externalServices service types that are already satisfied without a provider module
     *                         (e.g. JavaPlugin pre-registered by the platform adapter).
     *                         Modules may declare these in {@code requires} for DI purposes;
     *                         they are ignored when checking for missing providers and do not
     *                         create edges in the dependency graph.
     */
    public static DependencyGraph build(List<ModuleDescriptor> descriptorList) {
        return build(descriptorList, Set.of());
    }

    public static DependencyGraph build(List<ModuleDescriptor> descriptorList, Set<Class<?>> externalServices) {
        Map<String, ModuleDescriptor> descriptors = new LinkedHashMap<>();
        for (ModuleDescriptor d : descriptorList) {
            descriptors.put(d.id(), d);
        }

        Map<Class<?>, String> serviceProviders = new HashMap<>();
        Map<String, String> faultReasons = new LinkedHashMap<>();
        Set<String> systemModules = new HashSet<>();

        // Map service types to providers; detect duplicates
        for (ModuleDescriptor d : descriptorList) {
            if (d.system()) systemModules.add(d.id());
            if (faultReasons.containsKey(d.id())) continue;
            for (Class<?> type : d.provides()) {
                String existing = serviceProviders.put(type, d.id());
                if (existing != null) {
                    faultReasons.put(d.id(),
                        "duplicate provider for " + type.getName() + " (already provided by " + existing + ")");
                    serviceProviders.put(type, existing); // first wins
                }
            }
        }

        // Resolve module dependencies from requires -> provider module id
        Map<String, Set<String>> moduleDependencies = new LinkedHashMap<>();
        for (ModuleDescriptor d : descriptorList) {
            moduleDependencies.put(d.id(), new LinkedHashSet<>());
            if (faultReasons.containsKey(d.id())) continue;
            for (Class<?> required : d.requires()) {
                if (externalServices.contains(required)) continue; // satisfied externally, no edge needed
                String provider = serviceProviders.get(required);
                if (provider == null) {
                    faultReasons.put(d.id(), "no provider found for required service " + required.getName());
                    break;
                }
                moduleDependencies.get(d.id()).add(provider);
            }
        }

        // Topological sort (detects cycles)
        TopologicalSort.TopologicalResult result = TopologicalSort.sort(moduleDependencies, systemModules);

        // Mark cyclic modules faulty
        for (String cyclic : result.cyclic()) {
            faultReasons.putIfAbsent(cyclic, "circular dependency detected");
        }

        // Build reverse map: module id -> set of modules that depend on it
        Map<String, Set<String>> moduleDependents = new LinkedHashMap<>();
        for (String id : descriptors.keySet()) {
            moduleDependents.put(id, new LinkedHashSet<>());
        }
        for (Map.Entry<String, Set<String>> entry : moduleDependencies.entrySet()) {
            String dependent = entry.getKey();
            for (String dep : entry.getValue()) {
                moduleDependents.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(dependent);
            }
        }

        return new DependencyGraph(
            Collections.unmodifiableMap(descriptors),
            Collections.unmodifiableMap(serviceProviders),
            Collections.unmodifiableMap(moduleDependencies),
            Collections.unmodifiableMap(moduleDependents),
            Collections.unmodifiableMap(faultReasons),
            result.order()
        );
    }

    public List<String> loadOrder() {
        return topologicalOrder;
    }

    /** All modules (direct and transitive) that depend on the given module. */
    public Set<String> dependentsOf(String moduleId) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.offer(moduleId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String dep : moduleDependents.getOrDefault(current, Set.of())) {
                if (result.add(dep)) queue.offer(dep);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** Direct dependencies of the given module (module ids). */
    public Set<String> directDependenciesOf(String moduleId) {
        return Collections.unmodifiableSet(
            moduleDependencies.getOrDefault(moduleId, Set.of()));
    }

    /** Ordered list of transitive dependencies (dependencies first). */
    public List<String> dependencyChain(String moduleId) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        buildChain(moduleId, chain, visited);
        chain.remove(moduleId);
        return Collections.unmodifiableList(chain);
    }

    private void buildChain(String id, List<String> chain, Set<String> visited) {
        if (!visited.add(id)) return;
        for (String dep : moduleDependencies.getOrDefault(id, Set.of())) {
            buildChain(dep, chain, visited);
        }
        chain.add(id);
    }

    public boolean isFaulty(String moduleId) {
        return faultReasons.containsKey(moduleId);
    }

    public Optional<String> faultReason(String moduleId) {
        return Optional.ofNullable(faultReasons.get(moduleId));
    }

    public boolean hasModule(String moduleId) {
        return descriptors.containsKey(moduleId);
    }

    public Map<Class<?>, String> serviceProviders() {
        return serviceProviders;
    }
}
