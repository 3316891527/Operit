package com.ai.assistance.operit.ui.main.screens

import com.ai.assistance.operit.data.theme.script.ThemeStyleTargets
import org.junit.Assert.*
import org.junit.Test

class ThemePageIdsTest {
    @Test fun pageSelectorsDoNotContainDynamicRouteArguments() {
        assertEquals("page.packages", Screen.Packages.themePageId)
        assertEquals(Screen.MarketCategory("one").themePageId, Screen.MarketCategory("two").themePageId)
        assertEquals(Screen.PackageDetail("private-name", "Private title").themePageId, Screen.PackageDetail("other", "Other").themePageId)
        assertTrue(Screen.MemorySpaceDetail("private-space").themePageId in ThemeStyleTargets.pageIds)
    }
}
