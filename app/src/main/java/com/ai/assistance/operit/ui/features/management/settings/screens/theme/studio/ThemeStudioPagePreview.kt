package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.LocalAppNavigationModel
import com.ai.assistance.operit.ui.main.navigation.RouteRuntime
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.themePageId
import com.ai.assistance.operit.ui.theme.script.themePreviewFocus
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

@Composable
internal fun StudioPagePreview(state: ThemeStudioState) {
    val pageId = state.target.id
    val screen = state.pagePreview.screen(pageId)
    Box(Modifier.fillMaxSize().themePreviewFocus(pageId)) {
        if (screen != null) StudioLivePage(screen)
        else StudioTheme {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                Text(studioPageLabel(pageId), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.studio_live_page_choose_hint))
                Button(onClick = { state.pagePreview.choose(pageId) }) { Text(stringResource(R.string.studio_live_page_choose)) }
            }
        }
    }
}

@Composable
internal fun StudioPagePreviewActions(state: ThemeStudioState) {
    if (state.scene != StudioScene.Pages) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.studio_live_page_read_only), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.pagePreview.needsObject(state.target.id)) TextButton(onClick = { state.pagePreview.choose(state.target.id) }) { Text(stringResource(R.string.studio_live_page_choose)) }
    }
}

@Composable
internal fun StudioPagePreviewObjectPicker(state: ThemeStudioState) {
    val pageId = state.pagePreview.selectingPage ?: return
    val target = ThemeStudioPagePreviewTargets.getValue(pageId)
    StudioFullSheet(stringResource(R.string.studio_live_page_choose_for, studioPageLabel(pageId)), state.pagePreview::closePicker) {
        Text(stringResource(R.string.studio_live_page_picker_hint), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        when (target) {
            is StudioPagePreviewTarget.Page -> error("This page does not need a preview object")
            is StudioPagePreviewTarget.Choose -> Box(Modifier.fillMaxWidth().weight(1f)) {
                StudioLivePage(target.start, readOnly = false, onNavigate = { selected ->
                    if (selected.themePageId == pageId) { state.pagePreview.select(pageId, selected); true } else false
                }, onExit = state.pagePreview::closePicker)
            }
            StudioPagePreviewTarget.Plugin -> {
                val model = requireNotNull(LocalAppNavigationModel.current)
                val routes = model.routes.filter { it.runtime == RouteRuntime.TOOLPKG_COMPOSE_DSL }
                if (routes.isEmpty()) Text(stringResource(R.string.studio_live_page_no_plugins), Modifier.padding(16.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(routes, key = { it.routeId }) { route ->
                        ListItem(
                            headlineContent = { Text(requireNotNull(route.title)) },
                            supportingContent = { Text(requireNotNull(route.ownerPackageName)) },
                            modifier = Modifier.clickable {
                                val container = requireNotNull(route.ownerPackageName)
                                val module = requireNotNull(route.toolPkgUiModuleId)
                                val title = requireNotNull(route.title)
                                val selected = Screen.ToolPkgComposeDsl(container, module, title, route.keepAlive)
                                state.pagePreview.select(pageId, selected)
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
