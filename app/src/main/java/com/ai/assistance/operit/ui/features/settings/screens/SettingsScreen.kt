package com.ai.assistance.operit.ui.features.settings.screens

import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.glass.rememberGlassAppearance
import com.ai.assistance.operit.ui.theme.liquidGlass

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

// 保存滑动状态变量，使其跨重组保持
private val SettingsScreenScrollPosition = mutableStateOf(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        navigateToToolPermissions: () -> Unit,
        navigateToModelConfig: () -> Unit,
        navigateToThemeSettings: () -> Unit,
        navigateToGlobalDisplaySettings: () -> Unit,
        navigateToModelPrompts: () -> Unit,
        navigateToMemory: () -> Unit,
        navigateToFunctionalConfig: () -> Unit,
        navigateToDataStorageManagement: () -> Unit,
        navigateToChatHistorySettings: () -> Unit,
        navigateToChatBackupSettings: () -> Unit,
        navigateToLanguageSettings: () -> Unit,
        navigateToSpeechServicesSettings: () -> Unit,
        navigateToExternalHttpChatSettings: () -> Unit,
        navigateToWaifuModeSettings: () -> Unit,
        navigateToTokenUsageStatistics: () -> Unit,
        navigateToPerformanceMonitor: () -> Unit,
        navigateToContextSummarySettings: () -> Unit,
        navigateToLayoutAdjustmentSettings: () -> Unit,
        navigateToLogcat: () -> Unit,
        navigateToAbout: () -> Unit
) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var showClearCookieConfirm by remember { mutableStateOf(false) }

        // 创建和记住滚动状态，设置为上次保存的位置
        val scrollState = rememberScrollState(SettingsScreenScrollPosition.value)

        // 当滚动状态改变时更新保存的位置
        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        SettingsScreenScrollPosition.value = position
                }
        }

        val cardContainerColor = MaterialTheme.colorScheme.surfaceContainer

        Column(
                modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(scrollState)
        ) {
                // ======= 个性化配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_personalization),
                        icon = Icons.Default.Person,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(R.string.language_settings),
                                subtitle = stringResource(id = R.string.settings_language_subtitle),
                                icon = Icons.Default.Language,
                                onClick = navigateToLanguageSettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_theme_appearance),
                                subtitle = stringResource(id = R.string.settings_theme_subtitle),
                                icon = Icons.Default.Palette,
                                onClick = navigateToThemeSettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(R.string.settings_global_display),
                                subtitle = stringResource(R.string.settings_global_display_subtitle),
                                icon = Icons.Default.Visibility,
                                onClick = navigateToGlobalDisplaySettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(R.string.layout_adjustment),
                                subtitle = stringResource(R.string.layout_adjustment_subtitle),
                                icon = Icons.Default.AspectRatio,
                                onClick = navigateToLayoutAdjustmentSettings
                        )
                }

                // ======= AI模型配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_ai_model),
                        icon = Icons.Default.Settings,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_model_parameters),
                                subtitle = stringResource(id = R.string.settings_model_params_subtitle),
                                icon = Icons.Default.Api,
                                onClick = navigateToModelConfig
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_functional_model),
                                subtitle = stringResource(id = R.string.settings_functional_model_subtitle),
                                icon = Icons.Default.Tune,
                                onClick = navigateToFunctionalConfig
                        )

                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_section_context_summary),
                                subtitle = stringResource(id = R.string.settings_context_summary_subtitle),
                                icon = Icons.Default.Tune,
                                onClick = navigateToContextSummarySettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_speech_services),
                                subtitle = stringResource(id = R.string.settings_speech_services_subtitle),
                                icon = Icons.Default.RecordVoiceOver,
                                onClick = navigateToSpeechServicesSettings
                        )
                        
                }

                // ======= 提示词配置 =======
                SettingsSection(
                        title = stringResource(R.string.settings_prompt_section),
                        icon = Icons.Default.Message,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(R.string.settings_prompt_title),
                                subtitle = stringResource(id = R.string.settings_system_prompts_subtitle),
                                icon = Icons.Default.ChatBubble,
                                onClick = navigateToModelPrompts
                        )

                        CompactSettingsItem(
                                title = stringResource(R.string.memory_management_title),
                                subtitle = stringResource(R.string.memory_management_subtitle),
                                icon = Icons.Default.DeviceHub,
                                onClick = navigateToMemory
                        )

                        // 新增：Waifu模式设置
                        CompactSettingsItem(
                                title = stringResource(R.string.waifu_mode_settings),
                                subtitle = stringResource(R.string.waifu_mode_settings_desc),
                                icon = Icons.Default.EmojiEmotions,
                                onClick = navigateToWaifuModeSettings
                        )
                }

                // ======= 数据和权限 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_data_permissions),
                        icon = Icons.Default.Security,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_tool_permissions),
                                subtitle = stringResource(id = R.string.settings_tool_permissions_subtitle),
                                icon = Icons.Default.AdminPanelSettings,
                                onClick = navigateToToolPermissions
                        )

                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_data_backup),
                                subtitle = stringResource(id = R.string.settings_data_backup_desc),
                                icon = Icons.Default.CloudUpload,
                                onClick = navigateToChatBackupSettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_chat_history_management),
                                subtitle = stringResource(id = R.string.settings_chat_history_management_subtitle),
                                icon = Icons.Default.ManageHistory,
                                onClick = navigateToChatHistorySettings
                        )

                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_token_usage_stats),
                                subtitle = stringResource(id = R.string.settings_token_usage_subtitle),
                                icon = Icons.Default.Analytics,
                                onClick = navigateToTokenUsageStatistics
                        )

                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_performance_monitor),
                                subtitle = stringResource(id = R.string.settings_performance_monitor_subtitle),
                                icon = Icons.Default.Speed,
                                onClick = navigateToPerformanceMonitor
                        )

                        CompactSettingsItem(
                                title = stringResource(R.string.nav_about),
                                subtitle = stringResource(R.string.settings_about_subtitle),
                                icon = Icons.Default.Info,
                                onClick = navigateToAbout
                        )
                }

                // 日志入口保留在设置，开发工具不再占用设置列表。
                SettingsSection(
                        title = stringResource(R.string.settings_diagnostics),
                        icon = Icons.Default.BugReport,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(R.string.tool_log_viewer),
                                subtitle = stringResource(R.string.logcat_description),
                                icon = Icons.Default.Description,
                                onClick = navigateToLogcat
                        )
                }

                // ======= 隐私与数据清理 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_privacy_data_cleanup),
                        icon = Icons.Default.DeleteSweep,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(R.string.offline_resources_title),
                                subtitle = stringResource(R.string.offline_resources_subtitle),
                                icon = Icons.Default.Download,
                                onClick = {
                                        context.startActivity(android.content.Intent(context, com.ai.assistance.operit.ui.features.resources.OfflineResourcesActivity::class.java))
                                }
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_data_storage),
                                subtitle = stringResource(id = R.string.settings_data_storage_subtitle),
                                icon = Icons.Default.Storage,
                                onClick = navigateToDataStorageManagement
                        )
                }

                // ======= 外部调用 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_external_calls),
                        icon = Icons.Default.SettingsEthernet,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_external_http_chat),
                                subtitle = stringResource(id = R.string.settings_external_http_chat_subtitle),
                                icon = Icons.Default.SettingsEthernet,
                                onClick = navigateToExternalHttpChatSettings
                        )
                }

                // 底部间距
                Spacer(modifier = Modifier.height(16.dp))
        }
}
@Composable
private fun SettingsSection(
        title: String,
        icon: ImageVector,
        containerColor: Color,
        content: @Composable ColumnScope.() -> Unit
) {
        val glassEnabled = rememberGlassAppearance().settingsCards
        // 开启颜色定制后让分组标题与图标跟随主色，默认主题继续使用黑白正文色。
        val sectionTitleColor = if (LocalThemePreferenceSnapshot.current.useCustomColors) {
                MaterialTheme.colorScheme.primary
        } else {
                MaterialTheme.colorScheme.onSurface
        }
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                            .liquidGlass(glassEnabled, RoundedCornerShape(10.dp), containerColor)
                            .padding(if (glassEnabled) 8.dp else 0.dp)
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = sectionTitleColor,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = sectionTitleColor
                        )
                }
                
                // 内容区域
                Card(
                        modifier = Modifier.fillMaxWidth()
                            .liquidGlass(glassEnabled, MaterialTheme.shapes.medium, containerColor),
                        colors = CardDefaults.cardColors(
                                containerColor = if (glassEnabled) Color.Transparent else containerColor,
                                contentColor = MaterialTheme.colorScheme.onSurface
                        )
                ) {
                        Column(
                                modifier = Modifier.padding(12.dp),
                                content = content
                        )
                }
        }
}

@Composable
private fun CompactSettingsItem(
        title: String,
        subtitle: String,
        icon: ImageVector,
        onClick: () -> Unit
) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClick() }
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
                
                Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                )
        }
}
