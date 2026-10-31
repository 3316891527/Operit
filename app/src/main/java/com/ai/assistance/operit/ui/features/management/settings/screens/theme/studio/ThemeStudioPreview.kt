package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemePreviewBackground
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemePreviewMessage
import com.ai.assistance.operit.ui.theme.*
import com.ai.assistance.operit.ui.theme.script.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

/** 与正式页面共用脚本宿主、角色参数、字体和绘制函数，点击样例只选择制作对象。 */
@Composable
internal fun StudioPreview(state: ThemeStudioState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val values by state.session.values.collectAsState()
    val userName by state.session.globalUserNameFlow.collectAsState()
    val current = LocalThemePreferenceSnapshot.current
    val snapshot = remember(current, values, state.mode) { current.copy(values = values.withBoolean("use_system_theme", false).withString("theme_mode", state.mode)) }
    val colors = remember(context, snapshot) { resolveThemeColorScheme(context, snapshot) }
    val typography = remember(context, values) {
        createCustomTypography(context, values.requiredBoolean("use_custom_font"), values.requiredString("font_type"), values.requiredString("system_font_name"), values.string("custom_font_path"), values.requiredFloat("font_scale"))
    }
    val backdrop = rememberLayerBackdrop()
    val water = if (isWaterGlassSupported()) rememberLiquidState() else null
    Box(modifier.clip(RoundedCornerShape(10.dp))) {
        CompositionLocalProvider(LocalThemePreferenceSnapshot provides snapshot, LocalLiquidGlassBackdrop provides backdrop, LocalWaterGlassState provides water) {
            MaterialTheme(colorScheme = colors, typography = typography) {
                ThemeScriptHost(snapshot, draft = state.bundle) {
                    val previewStates = if (state.target.kind == StudioKind.Surface && state.property == StudioProperty.States) mapOf(state.target.id to state.previewState) else emptyMap()
                    CompositionLocalProvider(LocalThemeSurfaceStates provides previewStates) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.matchParentSize().layerBackdrop(backdrop).then(if (water == null) Modifier else Modifier.liquefiable(water)).background(MaterialTheme.colorScheme.background)) {
                                ThemePreviewBackground(values)
                                Box(Modifier.fillMaxSize().themeScriptDecoration("background", values.requiredFloat("background_image_opacity")))
                            }
                            when (state.scene) {
                                StudioScene.Chat -> StudioChatPreview(state, values, userName)
                                StudioScene.Components -> StudioComponentsPreview(state)
                                StudioScene.Navigation -> StudioNavigationPreview(state)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioPick(state: ThemeStudioState, id: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val target = ThemeStudioTargets.first { it.id == id }
    Box(modifier.border(if (state.target == target) 1.dp else 0.dp, if (state.target == target) StudioAccent else Color.Transparent, RoundedCornerShape(5.dp)).clickable { state.select(target) }) { content() }
}

@Composable
private fun StudioChatPreview(state: ThemeStudioState, values: ThemePreferenceValues, userName: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StudioPreviewAppBar(state)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StudioPick(state, "userMessage", Modifier.fillMaxWidth()) { ThemePreviewMessage(values, user = true, userName = userName) }
                StudioPick(state, "assistantMessage", Modifier.fillMaxWidth()) { ThemePreviewMessage(values, user = false, userName = userName) }
                StudioPick(state, "status") { ThemeScriptSurface("status") { Text(stringResource(R.string.theme_workbench_example_complete), Modifier.padding(3.dp), style = MaterialTheme.typography.labelSmall) } }
            }
        }
        if (state.target.id in setOf("userAvatar", "assistantAvatar", "iconButton")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (state.target.id == "iconButton") StudioPick(state, "iconButton") { ThemeScriptIconButton(onClick = {}) { Icon(Icons.Default.Add, null) } }
                else StudioPick(state, state.target.id) {
                    val id = state.target.id
                    Surface(shape = themeScriptShape(id, RoundedCornerShape(50)), color = themeScriptFill(id, MaterialTheme.colorScheme.secondaryContainer)) {
                        Box(Modifier.size(themeScriptAvatarSize(id, 30.dp)).themeScriptDecoration(id, drawFill = false), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null) }
                    }
                }
            }
        }
        StudioPick(state, "composer", Modifier.fillMaxWidth()) {
            Surface(shape = themeScriptShape("composer", RoundedCornerShape(18.dp)), color = themeScriptFill("composer", MaterialTheme.colorScheme.surfaceContainer)) {
                ThemeScriptSurface("composer", drawFill = false) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        StudioPreviewIcon(state, "composer.settings")
                        StudioPick(state, "input", Modifier.weight(1f)) {
                            ThemeScriptSurface("input") { Text(stringResource(R.string.theme_workbench_example_input), Modifier.fillMaxWidth().padding(7.dp), style = MaterialTheme.typography.bodySmall) }
                        }
                        StudioPreviewIcon(state, if (state.target.kind == StudioKind.Icon && state.target.id.startsWith("composer.") && state.target.id != "composer.settings") state.target.id else "composer.send")
                    }
                }
            }
        }

    }
}

@Composable
private fun StudioComponentsPreview(state: ThemeStudioState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val selected = state.target.id.takeIf { state.target.kind == StudioKind.Surface && it in setOf("dialog", "sheet", "menu", "listItem", "toolCard") } ?: "settingsCard"
        StudioPick(state, selected, Modifier.fillMaxWidth()) {
            Surface(shape = themeScriptShape(selected, RoundedCornerShape(14.dp)), color = themeScriptFill(selected, MaterialTheme.colorScheme.surfaceContainer)) {
                ThemeScriptSurface(selected, drawFill = false) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(ThemeStudioTargets.first { it.id == selected }.label(), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.studio_component_sample), style = MaterialTheme.typography.bodyMedium)
                        StudioPick(state, "button") { ThemeScriptButton(onClick = { state.select(ThemeStudioTargets.first { it.id == "button" }) }, enabled = state.previewState != "disabled") { Text(stringResource(R.string.studio_sample_button)) } }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeScriptOutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.theme_appearance_disabled)) }
            ThemeScriptTextButton(onClick = {}) { Text(stringResource(R.string.studio_sample_action)) }
        }
    }
}

@Composable
private fun StudioNavigationPreview(state: ThemeStudioState) {
    Column(Modifier.fillMaxSize()) {
        StudioPreviewAppBar(state)
        Row(Modifier.fillMaxWidth().weight(1f)) {
            StudioPick(state, "navigation", Modifier.width(152.dp).fillMaxHeight()) {
                Surface(color = themeScriptFill("navigation", MaterialTheme.colorScheme.surfaceContainerLow), shape = themeScriptShape("navigation", RoundedCornerShape(0.dp))) {
                    ThemeScriptSurface("navigation", drawFill = false) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudioPick(state, "navigationItem", Modifier.fillMaxWidth()) {
                                ThemeScriptSurface("navigationItem", selected = true) {
                                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StudioPreviewIcon(state, "navigation.item")
                                        Text(stringResource(R.string.studio_scene_chat), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Text(stringResource(R.string.studio_sample_history), Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Operit", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.studio_component_sample), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StudioPreviewIcon(state: ThemeStudioState, id: String) {
    val vector: ImageVector = when (id) {
        "composer.settings" -> Icons.Default.Settings
        "composer.attachment" -> Icons.Default.Add
        "composer.microphone" -> Icons.Default.Mic
        "composer.send" -> Icons.AutoMirrored.Filled.Send
        "composer.stop" -> Icons.Default.Stop
        "composer.fullscreen" -> Icons.Default.Fullscreen
        "composer.queue" -> Icons.Default.Queue
        "composer.expand" -> Icons.Default.ExpandMore
        "appBar.back" -> Icons.AutoMirrored.Filled.ArrowBack
        "appBar.menu" -> Icons.Default.Menu
        "appBar.history" -> Icons.Default.History
        "appBar.floating" -> Icons.Default.PictureInPictureAlt
        "navigation.item" -> Icons.Default.Chat
        else -> error("Unknown studio icon")
    }
    ThemeScriptIconButton(onClick = { state.select(ThemeStudioTargets.first { it.id == id }) }, modifier = Modifier.size(34.dp)) {
        ThemeScriptIcon(id, vector, ThemeStudioTargets.first { it.id == id }.label(), Modifier.size(18.dp))
    }
}
@Composable
private fun StudioPreviewAppBar(state: ThemeStudioState) {
    StudioPick(state, "appBar", Modifier.fillMaxWidth()) {
            Surface(color = themeScriptFill("appBar", MaterialTheme.colorScheme.surfaceContainer), shape = themeScriptShape("appBar", RoundedCornerShape(0.dp))) {
                ThemeScriptSurface("appBar", drawFill = false) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        StudioPreviewIcon(state, "appBar.back")
                        Text(state.targetName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        StudioPreviewIcon(state, if (state.target.kind == StudioKind.Icon && state.target.id.startsWith("appBar.") && state.target.id != "appBar.back") state.target.id else "appBar.menu")
                    }
                }
            }
        }
}
