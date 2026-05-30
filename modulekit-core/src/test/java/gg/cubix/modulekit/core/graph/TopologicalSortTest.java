package gg.cubix.modulekit.core.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TopologicalSortTest {

    @Test
    void acyclicOrderRespectsDependencies() {
        Map<String, Set<String>> deps = Map.of(
            "a", Set.of(),
            "b", Set.of("a"),
            "c", Set.of("b")
        );
        TopologicalSort.TopologicalResult result = TopologicalSort.sort(deps);
        assertTrue(result.cyclic().isEmpty());
        assertTrue(result.order().indexOf("a") < result.order().indexOf("b"));
        assertTrue(result.order().indexOf("b") < result.order().indexOf("c"));
    }

    @Test
    void cyclicNodesDetected() {
        Map<String, Set<String>> deps = Map.of(
            "a", Set.of("b"),
            "b", Set.of("a")
        );
        TopologicalSort.TopologicalResult result = TopologicalSort.sort(deps);
        assertEquals(Set.of("a", "b"), result.cyclic());
    }

    @Test
    void cyclicNodesAppendedAtEnd() {
        Map<String, Set<String>> deps = Map.of(
            "good", Set.of(),
            "a", Set.of("b"),
            "b", Set.of("a")
        );
        TopologicalSort.TopologicalResult result = TopologicalSort.sort(deps);
        assertEquals("good", result.order().get(0));
        assertTrue(result.order().contains("a"));
        assertTrue(result.order().contains("b"));
    }

    @Test
    void systemModulesComeFirst() {
        Map<String, Set<String>> deps = Map.of(
            "regular", Set.of(),
            "system", Set.of()
        );
        TopologicalSort.TopologicalResult result = TopologicalSort.sort(deps, Set.of("system"));
        assertEquals("system", result.order().get(0));
        assertEquals("regular", result.order().get(1));
    }

    @Test
    void singleNode() {
        TopologicalSort.TopologicalResult result = TopologicalSort.sort(Map.of("alone", Set.of()));
        assertEquals(1, result.order().size());
        assertTrue(result.cyclic().isEmpty());
    }

    @Test
    void emptyGraph() {
        TopologicalSort.TopologicalResult result = TopologicalSort.sort(Map.of());
        assertTrue(result.order().isEmpty());
        assertTrue(result.cyclic().isEmpty());
    }
}
