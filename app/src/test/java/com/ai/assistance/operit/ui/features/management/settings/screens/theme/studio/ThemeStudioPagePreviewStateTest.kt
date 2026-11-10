package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import com.ai.assistance.operit.data.theme.script.ThemeStyleTargets
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry
import com.ai.assistance.operit.ui.main.screens.themePageId
import org.junit.Assert.*
import org.junit.Test

class ThemeStudioPagePreviewStateTest {
    @Test
    fun allPageTargetsResolveToTheRealScreenOrAnExplicitObjectSelection() {
        assertEquals(ThemeStyleTargets.pageIds, ThemeStudioPagePreviewTargets.keys)
        ThemeStudioPagePreviewTargets.forEach { (pageId, target) ->
            when (target) {
                is StudioPagePreviewTarget.Page -> assertEquals(pageId, target.screen.themePageId)
                is StudioPagePreviewTarget.Choose -> {
                    assertTrue(target.start.themePageId in ThemeStyleTargets.pageIds)
                    assertNotEquals(pageId, target.start.themePageId)
                }
                StudioPagePreviewTarget.Plugin -> assertEquals("page.toolPkgComposeDsl", pageId)
            }
        }
    }

    @Test
    fun parameterizedPagesDoNotPretendThatTheSelectionPageIsTheirPreview() {
        val state = StudioPagePreviewState()
        listOf("page.packageDetail", "page.workflowDetail", "page.marketEntryDetail", "page.toolPkgComposeDsl").forEach { pageId ->
            assertTrue(state.needsObject(pageId))
            assertNull(state.screen(pageId))
        }
        assertEquals(Screen.Settings, state.screen("page.settings"))
        assertFalse(state.needsObject("page.settings"))
    }

    @Test
    fun selectingAnActualObjectPreservesItsArgumentsAndClosesThePicker() {
        val state = StudioPagePreviewState()
        val screen = Screen.PackageDetail("installed.package", "Installed package")
        state.choose("page.packageDetail")
        assertEquals("page.packageDetail", state.selectingPage)
        state.select("page.packageDetail", screen)
        assertSame(screen, state.screen("page.packageDetail"))
        assertNull(state.selectingPage)
    }

    @Test
    fun aWrongPageCannotOverwriteTheSelectedContext() {
        val state = StudioPagePreviewState()
        val screen = Screen.PackageDetail("installed.package", "Installed package")
        state.select("page.packageDetail", screen)
        assertThrows(IllegalArgumentException::class.java) { state.select("page.packageDetail", Screen.WorkflowDetail("workflow-123")) }
        assertSame(screen, state.screen("page.packageDetail"))
        assertThrows(IllegalArgumentException::class.java) { state.choose("page.settings") }
        assertThrows(IllegalArgumentException::class.java) { state.select("page.settings", Screen.Settings) }
    }

    @Test
    fun cancellingKeepsThePreviousObjectAndEachPageHasItsOwnSelection() {
        val state = StudioPagePreviewState()
        val packageScreen = Screen.PackageDetail("installed.package", "Installed package")
        val workflowScreen = Screen.WorkflowDetail("workflow-123")
        state.select("page.packageDetail", packageScreen)
        state.select("page.workflowDetail", workflowScreen)
        state.choose("page.packageDetail")
        state.closePicker()
        assertNull(state.selectingPage)
        assertSame(packageScreen, state.screen("page.packageDetail"))
        assertSame(workflowScreen, state.screen("page.workflowDetail"))
    }

    @Test
    fun realScreensKeepTheirArgumentsThroughTheSameRouterUsedByTheApp() {
        val direct = ThemeStudioPagePreviewTargets.values.filterIsInstance<StudioPagePreviewTarget.Page>().map { it.screen }
        val objects = listOf(
            Screen.PackageDetail("installed.package", "Installed package"),
            Screen.WorkflowDetail("workflow-123"),
            Screen.ToolPkgComposeDsl("installed.plugin", "main", "Plugin", true),
            Screen.ToolPkgPluginConfig("installed.plugin", "settings", "Plugin settings", true),
        )
        (direct + objects).forEach { screen ->
            assertEquals(screen, ScreenRouteRegistry.screenFromEntry(ScreenRouteRegistry.toEntry(screen)))
        }
    }

    @Test
    fun pluginConfigurationComesFromTheActualPackageDetailFlow() {
        assertEquals(StudioPagePreviewTarget.Choose(Screen.Packages), ThemeStudioPagePreviewTargets.getValue("page.toolPkgPluginConfig"))
        val state = StudioPagePreviewState()
        val config = Screen.ToolPkgPluginConfig("installed.plugin", "settings", "Plugin settings", true)
        state.choose("page.toolPkgPluginConfig")
        state.select("page.toolPkgPluginConfig", config)
        assertSame(config, state.screen("page.toolPkgPluginConfig"))
        assertThrows(IllegalArgumentException::class.java) {
            state.select("page.toolPkgPluginConfig", Screen.ToolPkgComposeDsl("installed.plugin", "main", "Plugin"))
        }
    }

    @Test
    fun unknownPageIdsAreRejected() {
        assertThrows(NoSuchElementException::class.java) { StudioPagePreviewState().screen("page.unknown") }
    }
}
