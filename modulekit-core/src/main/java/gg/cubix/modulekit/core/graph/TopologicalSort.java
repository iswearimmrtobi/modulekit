package gg.cubix.modulekit.core.graph;

import java.util.*;

public final class TopologicalSort {

    private TopologicalSort() {}

    public record TopologicalResult(List<String> order, Set<String> cyclic) {}

    /**
     * Kahn's algorithm. {@code prioritized} nodes are processed before others at each level.
     *
     * @param dependencies map of node id → set of node ids it depends on
     * @param prioritized  nodes to prefer when multiple are ready at the same level
     */
    public static TopologicalResult sort(Map<String, Set<String>> dependencies, Set<String> prioritized) {
        Map<String, Integer> inDegree = new HashMap<>();
        // forward edge: dep -> dependent
        Map<String, Set<String>> dependentsOf = new HashMap<>();

        for (String node : dependencies.keySet()) {
            inDegree.putIfAbsent(node, 0);
        }

        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            String node = entry.getKey();
            for (String dep : entry.getValue()) {
                inDegree.merge(node, 1, Integer::sum);
                dependentsOf.computeIfAbsent(dep, k -> new HashSet<>()).add(node);
                inDegree.putIfAbsent(dep, 0);
            }
        }

        // Priority queue: prioritized nodes first, then alphabetical for determinism
        Queue<String> ready = new PriorityQueue<>(Comparator
            .comparingInt((String n) -> prioritized.contains(n) ? 0 : 1)
            .thenComparing(Comparator.naturalOrder()));

        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) ready.offer(entry.getKey());
        }

        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String node = ready.poll();
            order.add(node);
            for (String dependent : dependentsOf.getOrDefault(node, Set.of())) {
                if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                    ready.offer(dependent);
                }
            }
        }

        Set<String> cyclic = new LinkedHashSet<>();
        for (String node : inDegree.keySet()) {
            if (!order.contains(node)) cyclic.add(node);
        }
        order.addAll(cyclic);

        return new TopologicalResult(Collections.unmodifiableList(order), Collections.unmodifiableSet(cyclic));
    }

    public static TopologicalResult sort(Map<String, Set<String>> dependencies) {
        return sort(dependencies, Set.of());
    }
}
