package gg.cubix.modulekit.core.graph;

import gg.cubix.modulekit.api.module.ModuleDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphTest {

    interface FooService {}
    interface BarService {}

    @Test
    void providerBeforeConsumer() {
        ModuleDescriptor provider = ModuleDescriptor.builder("provider", "Provider")
            .provides(FooService.class).build();
        ModuleDescriptor consumer = ModuleDescriptor.builder("consumer", "Consumer")
            .requires(FooService.class).build();

        DependencyGraph graph = DependencyGraph.build(List.of(consumer, provider));

        assertFalse(graph.isFaulty("provider"));
        assertFalse(graph.isFaulty("consumer"));
        assertTrue(graph.loadOrder().indexOf("provider") < graph.loadOrder().indexOf("consumer"));
    }

    @Test
    void unresolvableRequirementIsFaulty() {
        ModuleDescriptor lonely = ModuleDescriptor.builder("lonely", "Lonely")
            .requires(FooService.class).build();

        DependencyGraph graph = DependencyGraph.build(List.of(lonely));

        assertTrue(graph.isFaulty("lonely"));
        assertTrue(graph.faultReason("lonely").isPresent());
    }

    @Test
    void duplicateProviderSecondIsFaulty() {
        ModuleDescriptor first = ModuleDescriptor.builder("first", "First")
            .provides(FooService.class).build();
        ModuleDescriptor second = ModuleDescriptor.builder("second", "Second")
            .provides(FooService.class).build();

        DependencyGraph graph = DependencyGraph.build(List.of(first, second));

        assertFalse(graph.isFaulty("first"));
        assertTrue(graph.isFaulty("second"));
    }

    @Test
    void circularDependencyBothFaulty() {
        ModuleDescriptor a = ModuleDescriptor.builder("a", "A")
            .provides(FooService.class).requires(BarService.class).build();
        ModuleDescriptor b = ModuleDescriptor.builder("b", "B")
            .provides(BarService.class).requires(FooService.class).build();

        DependencyGraph graph = DependencyGraph.build(List.of(a, b));

        assertTrue(graph.isFaulty("a"));
        assertTrue(graph.isFaulty("b"));
    }

    @Test
    void dependentsOfReturnTransitiveDependents() {
        ModuleDescriptor a = ModuleDescriptor.builder("a", "A").provides(FooService.class).build();
        ModuleDescriptor b = ModuleDescriptor.builder("b", "B")
            .requires(FooService.class).provides(BarService.class).build();
        ModuleDescriptor c = ModuleDescriptor.builder("c", "C").requires(BarService.class).build();

        DependencyGraph graph = DependencyGraph.build(List.of(a, b, c));

        assertTrue(graph.dependentsOf("a").contains("b"));
        assertTrue(graph.dependentsOf("a").contains("c"));
        assertFalse(graph.dependentsOf("a").contains("a"));
    }
}
