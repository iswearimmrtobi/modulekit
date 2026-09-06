package gg.cubix.modulekit.folia;

import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FoliaPlatformTest {

    @BeforeEach
    @AfterEach
    void clearMemoisedBrand() {
        FoliaPlatform.resetCache();
    }

    private ServerBuildInfo buildInfoReporting(Key... compatibleBrands) {
        ServerBuildInfo info = mock(ServerBuildInfo.class);
        when(info.isBrandCompatible(any())).thenReturn(false);
        for (Key brand : compatibleBrands) {
            when(info.isBrandCompatible(brand)).thenReturn(true);
        }
        return info;
    }

    @Test
    void isFolia_trueOnFoliaBrand() {
        // Folia reports compatible with both brands, since it is a Paper fork.
        ServerBuildInfo info = buildInfoReporting(FoliaPlatform.BRAND_FOLIA_ID, ServerBuildInfo.BRAND_PAPER_ID);

        try (MockedStatic<ServerBuildInfo> statics = mockStatic(ServerBuildInfo.class)) {
            statics.when(ServerBuildInfo::buildInfo).thenReturn(info);

            assertTrue(FoliaPlatform.isFolia());
            assertTrue(FoliaPlatform.isPaper());
            assertEquals("Folia (regionised multithreading)", FoliaPlatform.describe());
        }
    }

    @Test
    void isFolia_falseOnPlainPaper() {
        ServerBuildInfo info = buildInfoReporting(ServerBuildInfo.BRAND_PAPER_ID);

        try (MockedStatic<ServerBuildInfo> statics = mockStatic(ServerBuildInfo.class)) {
            statics.when(ServerBuildInfo::buildInfo).thenReturn(info);

            assertFalse(FoliaPlatform.isFolia());
            assertTrue(FoliaPlatform.isPaper());
            assertEquals("Paper", FoliaPlatform.describe());
        }
    }

    @Test
    void brandLookup_degradesToFalse_whenBuildInfoUnavailable() {
        // ServerBuildInfo.buildInfo() throws when the server has not registered the
        // service — e.g. this class loaded early, or a non-Paper server.
        try (MockedStatic<ServerBuildInfo> statics = mockStatic(ServerBuildInfo.class)) {
            statics.when(ServerBuildInfo::buildInfo).thenThrow(new java.util.NoSuchElementException());

            assertFalse(FoliaPlatform.isFolia());
            assertFalse(FoliaPlatform.isPaper());
        }
    }

    @Test
    void brandLookup_isMemoised() {
        ServerBuildInfo info = buildInfoReporting(FoliaPlatform.BRAND_FOLIA_ID);

        try (MockedStatic<ServerBuildInfo> statics = mockStatic(ServerBuildInfo.class)) {
            statics.when(ServerBuildInfo::buildInfo).thenReturn(info);
            assertTrue(FoliaPlatform.isFolia());
        }

        // Outside the mock scope the real lookup would fail; the cached answer stands.
        assertTrue(FoliaPlatform.isFolia());
    }
}
