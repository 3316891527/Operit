package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSwitchRow
import com.ai.assistance.operit.ui.theme.script.*
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField

internal enum class StudioPagePreviewKind { Chat, List, Detail, Editor, Settings, Terminal, Statistics, Document, Workflow, Voice }

/** 每个真实路由都有明确的预览类型，新增页面必须补充映射。 */
internal val ThemeStudioPagePreviewKinds = mapOf(
    "page.aiChat" to StudioPagePreviewKind.Chat,
    "page.memoryBase" to StudioPagePreviewKind.List,
    "page.memorySpaceDetail" to StudioPagePreviewKind.Detail,
    "page.packages" to StudioPagePreviewKind.List,
    "page.packageDetail" to StudioPagePreviewKind.Detail,
    "page.market" to StudioPagePreviewKind.List,
    "page.marketCategory" to StudioPagePreviewKind.List,
    "page.marketNotifications" to StudioPagePreviewKind.List,
    "page.builtInMarketDetail" to StudioPagePreviewKind.Detail,
    "page.marketEntryDetail" to StudioPagePreviewKind.Detail,
    "page.marketAuthor" to StudioPagePreviewKind.Detail,
    "page.marketManage" to StudioPagePreviewKind.List,
    "page.artifactPublish" to StudioPagePreviewKind.Editor,
    "page.artifactContinuePublish" to StudioPagePreviewKind.Editor,
    "page.artifactEdit" to StudioPagePreviewKind.Editor,
    "page.repoPublish" to StudioPagePreviewKind.Editor,
    "page.repoEdit" to StudioPagePreviewKind.Editor,
    "page.repoPublishVersion" to StudioPagePreviewKind.Editor,
    "page.shizukuCommands" to StudioPagePreviewKind.List,
    "page.settings" to StudioPagePreviewKind.Settings,
    "page.gitHubAccount" to StudioPagePreviewKind.Detail,
    "page.help" to StudioPagePreviewKind.Document,
    "page.about" to StudioPagePreviewKind.Document,
    "page.agreement" to StudioPagePreviewKind.Document,
    "page.updateHistory" to StudioPagePreviewKind.Document,
    "page.workflow" to StudioPagePreviewKind.Workflow,
    "page.workflowDetail" to StudioPagePreviewKind.Workflow,
    "page.tokenConfig" to StudioPagePreviewKind.Editor,
    "page.userPreferencesSettings" to StudioPagePreviewKind.Settings,
    "page.toolPermission" to StudioPagePreviewKind.Settings,
    "page.modelConfig" to StudioPagePreviewKind.Editor,
    "page.speechServicesSettings" to StudioPagePreviewKind.Settings,
    "page.mnnModelDownload" to StudioPagePreviewKind.List,
    "page.personaCardGeneration" to StudioPagePreviewKind.Editor,
    "page.waifuModeSettings" to StudioPagePreviewKind.Settings,
    "page.customEmojiManagement" to StudioPagePreviewKind.List,
    "page.tagMarket" to StudioPagePreviewKind.List,
    "page.modelPromptsSettings" to StudioPagePreviewKind.Editor,
    "page.functionalConfig" to StudioPagePreviewKind.Settings,
    "page.themeSettings" to StudioPagePreviewKind.Settings,
    "page.globalDisplaySettings" to StudioPagePreviewKind.Settings,
    "page.dataStorageManagement" to StudioPagePreviewKind.List,
    "page.localModelStorage" to StudioPagePreviewKind.List,
    "page.linuxEnvironmentStorage" to StudioPagePreviewKind.List,
    "page.workspaceMediaStorage" to StudioPagePreviewKind.List,
    "page.chatHistoryStorage" to StudioPagePreviewKind.List,
    "page.memoryLibraryStorage" to StudioPagePreviewKind.List,
    "page.backupExportStorage" to StudioPagePreviewKind.List,
    "page.configurationResourceStorage" to StudioPagePreviewKind.List,
    "page.chatHistorySettings" to StudioPagePreviewKind.Settings,
    "page.chatBackupSettings" to StudioPagePreviewKind.Settings,
    "page.languageSettings" to StudioPagePreviewKind.Settings,
    "page.tokenUsageStatistics" to StudioPagePreviewKind.Statistics,
    "page.performanceMonitor" to StudioPagePreviewKind.Statistics,
    "page.contextSummarySettings" to StudioPagePreviewKind.Settings,
    "page.toolPkgComposeDsl" to StudioPagePreviewKind.Terminal,
    "page.toolPkgPluginConfig" to StudioPagePreviewKind.Editor,
    "page.terminal" to StudioPagePreviewKind.Terminal,
    "page.terminalSetup" to StudioPagePreviewKind.Settings,
    "page.terminalAutoConfig" to StudioPagePreviewKind.Terminal,
    "page.uIDebugger" to StudioPagePreviewKind.Terminal,
    "page.logcat" to StudioPagePreviewKind.Terminal,
    "page.markdownDemo" to StudioPagePreviewKind.Document,
    "page.speechToText" to StudioPagePreviewKind.Voice,
    "page.defaultAssistantGuide" to StudioPagePreviewKind.Document,
    "page.htmlPackager" to StudioPagePreviewKind.Editor,
)

/** 使用产品的背景、卡片、列表和按钮绘制有内容的样例，不执行页面业务。 */
@Composable
internal fun StudioPagePreview(state: ThemeStudioState) {
    val pageId = state.target.id
    val kind = ThemeStudioPagePreviewKinds.getValue(pageId)
    ThemePageBackground(pageId) {
        Box(Modifier.fillMaxSize().themePreviewFocus(pageId)) {
            if (kind == StudioPagePreviewKind.Chat) {
                StudioChatPreview(state)
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        Text(studioPageLabel(pageId), style = MaterialTheme.typography.titleLarge, themeTarget = "settings.title")
                    }
                    Text(stringResource(R.string.studio_page_layout_sample), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (kind) {
                        StudioPagePreviewKind.List -> {
                            OutlinedTextField("", {}, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.studio_page_sample_search)) }, leadingIcon = { Icon(Icons.Default.Search, null) })
                            repeat(4) { index -> StudioPageSampleRow(index) }
                        }
                        StudioPagePreviewKind.Settings -> {
                            ThemeWorkbenchCard(studioPageLabel(pageId), icon = Icons.Default.Settings) {
                                ThemeWorkbenchSwitchRow(stringResource(R.string.studio_page_sample_sync), true, onCheckedChange = {})
                                ThemeWorkbenchSwitchRow(stringResource(R.string.studio_page_sample_notification), false, onCheckedChange = {})
                            }
                            StudioPageSampleRow(0)
                            StudioPageSampleRow(1)
                        }
                        StudioPagePreviewKind.Detail -> {
                            ThemeWorkbenchCard(studioPageLabel(pageId), icon = Icons.Default.Description) {
                                Text(stringResource(R.string.studio_page_sample_description), style = MaterialTheme.typography.bodyLarge, themeTarget = "package.description")
                                Text("v1.0.0", style = MaterialTheme.typography.labelMedium, themeTarget = "package.version")
                                ThemeScriptButton(onClick = {}) { Text(stringResource(R.string.studio_sample_action)) }
                            }
                            StudioPageSampleRow(0)
                            StudioPageSampleRow(1)
                        }
                        StudioPagePreviewKind.Editor -> {
                            ThemeWorkbenchCard(stringResource(R.string.studio_page_sample_edit), icon = Icons.Default.Edit) {
                                OutlinedTextField(stringResource(R.string.studio_component_sample), {}, readOnly = true,
                                    label = { Text(stringResource(R.string.studio_page_sample_name)) }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(stringResource(R.string.studio_page_sample_description), {}, readOnly = true, minLines = 3,
                                    label = { Text(stringResource(R.string.studio_page_sample_content)) }, modifier = Modifier.fillMaxWidth())
                                ThemeScriptButton(onClick = {}) { Text(stringResource(R.string.studio_save)) }
                            }
                        }
                        StudioPagePreviewKind.Terminal -> {
                            Column(Modifier.fillMaxWidth().heightIn(min = 260.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF172029)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("operit:~ $", color = Color(0xFF88D9B1), fontFamily = FontFamily.Monospace)
                                Text(stringResource(R.string.studio_page_sample_terminal), color = Color(0xFFDDE6EE), fontFamily = FontFamily.Monospace)
                                Text("[preview]  09:41:00  ready", color = Color(0xFF88D9B1), fontFamily = FontFamily.Monospace)
                            }
                        }
                        StudioPagePreviewKind.Statistics -> {
                            ThemeWorkbenchCard(studioPageLabel(pageId), icon = Icons.Default.BarChart) {
                                Text("12,480", style = MaterialTheme.typography.headlineLarge)
                                Text(stringResource(R.string.studio_page_sample_statistics), style = MaterialTheme.typography.bodyMedium)
                                listOf(.35f, .7f, .5f, .9f).forEach { fraction ->
                                    Box(Modifier.fillMaxWidth().height(14.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(7.dp))) {
                                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp)))
                                    }
                                }
                            }
                        }
                        StudioPagePreviewKind.Document -> {
                            ThemeWorkbenchCard(studioPageLabel(pageId), icon = Icons.Default.Description) {
                                Text(stringResource(R.string.studio_page_sample_heading), style = MaterialTheme.typography.titleMedium)
                                repeat(3) { Text(stringResource(R.string.studio_page_sample_description), style = MaterialTheme.typography.bodyLarge) }
                            }
                        }
                        StudioPagePreviewKind.Workflow -> {
                            ThemeWorkbenchCard(studioPageLabel(pageId), icon = Icons.Default.AccountTree) {
                                repeat(3) { index ->
                                    StudioPageSampleRow(index)
                                    if (index < 2) Icon(Icons.Default.ArrowDownward, null)
                                }
                                ThemeScriptButton(onClick = {}) { Text(stringResource(R.string.studio_sample_action)) }
                            }
                        }
                        StudioPagePreviewKind.Voice -> {
                            ThemeWorkbenchCard(studioPageLabel(pageId), icon = Icons.Default.Mic) {
                                Text(stringResource(R.string.studio_page_sample_voice), style = MaterialTheme.typography.bodyLarge)
                                Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    listOf(18, 32, 52, 40, 64, 30, 44, 22).forEach { height ->
                                        Box(Modifier.weight(1f).height(height.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                                    }
                                }
                                ThemeScriptButton(onClick = {}) { Icon(Icons.Default.Mic, null); Text(stringResource(R.string.studio_sample_action)) }
                            }
                        }
                        StudioPagePreviewKind.Chat -> error("Chat preview is rendered by the shared chat components")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioPageSampleRow(index: Int) {
    ThemeScriptSurface("listItem") {
        ListItem(
            headlineContent = { Text(stringResource(R.string.studio_page_sample_item, index + 1), themeTarget = "settings.title") },
            supportingContent = { Text(stringResource(R.string.studio_page_sample_description), themeTarget = "settings.description") },
            leadingContent = { Icon(Icons.Default.Description, null) },
            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent,
                headlineColor = themeScriptContentColor("listItem", MaterialTheme.colorScheme.onSurface),
                supportingColor = themeScriptContentColor("listItem", MaterialTheme.colorScheme.onSurfaceVariant)),
        )
    }
}
