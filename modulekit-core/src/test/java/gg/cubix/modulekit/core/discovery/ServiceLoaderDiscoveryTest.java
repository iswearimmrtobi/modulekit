package gg.cubix.modulekit.core.discovery;

import gg.cubix.modulekit.api.module.Module;
import gg.cubix.modulekit.api.module.ModuleDescriptor;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ServiceLoaderDiscoveryTest {

    static final Logger LOG = Logger.getLogger("test");

    enum TestState { ON, OFF }

    // ── Static-descriptor path ────────────────────────────────────────────────
    //
    // Having both a static descriptor() and an @Override instance descriptor() in
    // the same class is a Java compile error: static and instance methods share the
    // same namespace within a class.  The static path in resolveDescriptor() is
    // therefore only reachable for classes that do NOT extend Module (i.e. where
    // there is no abstract descriptor() constraint forcing an instance method).
    // We test it with a plain class to verify the reflection logic is correct.

    static class PureStaticDescriptor {
        public static ModuleDescriptor getDescriptor() { return new ModuleDescriptor("static-id", "Static"); }
    }

    // ── No-arg constructor fallback path ─────────────────────────────────────

    static class WithNoArgFallback extends Module<TestState> {
        @Override public ModuleDescriptor descriptor() { return new ModuleDescriptor("noarg-id", "NoArg"); }
    }

    // ── No accessible no-arg ctor, no static method → should return null ─────

    static class NoAccessor extends Module<TestState> {
        NoAccessor(String ignored) {}
        @Override public ModuleDescriptor descriptor() { throw new UnsupportedOperationException(); }
    }

    @Test
    void staticDescriptorMethod_isUsed_whenPresent() {
        ModuleDescriptor d = ServiceLoaderDiscovery.resolveDescriptor(PureStaticDescriptor.class, LOG);
        assertNotNull(d);
        assertEquals("static-id", d.id());
    }

    @Test
    void noArgFallback_usedForModuleSubclasses() {
        // Module subclasses must have an instance descriptor(); the static path is
        // unreachable for them.  Verify the no-arg fallback path works correctly.
        ModuleDescriptor d = ServiceLoaderDiscovery.resolveDescriptor(WithNoArgFallback.class, LOG);
        assertNotNull(d);
        assertEquals("noarg-id", d.id());
    }

    @Test
    void noAccessorAndNoNoArgCtor_returnsNull() {
        ModuleDescriptor d = ServiceLoaderDiscovery.resolveDescriptor(NoAccessor.class, LOG);
        assertNull(d);
    }
}
