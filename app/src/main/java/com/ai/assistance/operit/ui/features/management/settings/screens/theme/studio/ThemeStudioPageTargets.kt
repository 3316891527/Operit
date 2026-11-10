package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.themePageId

/** 需要对象的页面必须先选择真实对象，不构造假 ID 或以其他页面代替预览。 */
internal sealed interface StudioPagePreviewTarget {
    data class Page(val screen: Screen) : StudioPagePreviewTarget
    data class Choose(val start: Screen) : StudioPagePreviewTarget
    data object Plugin : StudioPagePreviewTarget
}

internal val ThemeStudioPagePreviewTargets: Map<String, StudioPagePreviewTarget> = mapOf(
    "page.aiChat" to StudioPagePreviewTarget.Page(Screen.AiChat),
    "page.memoryBase" to StudioPagePreviewTarget.Page(Screen.MemoryBase),
    "page.memorySpaceDetail" to StudioPagePreviewTarget.Choose(Screen.MemoryBase),
    "page.packages" to StudioPagePreviewTarget.Page(Screen.Packages),
    "page.packageDetail" to StudioPagePreviewTarget.Choose(Screen.Packages),
    "page.market" to StudioPagePreviewTarget.Page(Screen.Market()),
    "page.marketCategory" to StudioPagePreviewTarget.Choose(Screen.Market()),
    "page.marketNotifications" to StudioPagePreviewTarget.Page(Screen.MarketNotifications),
    "page.builtInMarketDetail" to StudioPagePreviewTarget.Choose(Screen.Market()),
    "page.marketEntryDetail" to StudioPagePreviewTarget.Choose(Screen.Market()),
    "page.marketAuthor" to StudioPagePreviewTarget.Choose(Screen.Market()),
    "page.marketManage" to StudioPagePreviewTarget.Page(Screen.MarketManage),
    "page.artifactPublish" to StudioPagePreviewTarget.Page(Screen.ArtifactPublish),
    "page.artifactContinuePublish" to StudioPagePreviewTarget.Choose(Screen.MarketManage),
    "page.artifactEdit" to StudioPagePreviewTarget.Choose(Screen.MarketManage),
    "page.repoPublish" to StudioPagePreviewTarget.Choose(Screen.MarketManage),
    "page.repoEdit" to StudioPagePreviewTarget.Choose(Screen.MarketManage),
    "page.repoPublishVersion" to StudioPagePreviewTarget.Choose(Screen.MarketManage),
    "page.shizukuCommands" to StudioPagePreviewTarget.Page(Screen.ShizukuCommands),
    "page.settings" to StudioPagePreviewTarget.Page(Screen.Settings),
    "page.gitHubAccount" to StudioPagePreviewTarget.Page(Screen.GitHubAccount),
    "page.help" to StudioPagePreviewTarget.Page(Screen.Help),
    "page.about" to StudioPagePreviewTarget.Page(Screen.About),
    "page.agreement" to StudioPagePreviewTarget.Page(Screen.Agreement),
    "page.updateHistory" to StudioPagePreviewTarget.Page(Screen.UpdateHistory),
    "page.workflow" to StudioPagePreviewTarget.Page(Screen.Workflow),
    "page.workflowDetail" to StudioPagePreviewTarget.Choose(Screen.Workflow),
    "page.tokenConfig" to StudioPagePreviewTarget.Page(Screen.TokenConfig),
    "page.userPreferencesSettings" to StudioPagePreviewTarget.Page(Screen.UserPreferencesSettings()),
    "page.toolPermission" to StudioPagePreviewTarget.Page(Screen.ToolPermission),
    "page.modelConfig" to StudioPagePreviewTarget.Page(Screen.ModelConfig),
    "page.speechServicesSettings" to StudioPagePreviewTarget.Page(Screen.SpeechServicesSettings),
    "page.mnnModelDownload" to StudioPagePreviewTarget.Page(Screen.MnnModelDownload),
    "page.personaCardGeneration" to StudioPagePreviewTarget.Page(Screen.PersonaCardGeneration),
    "page.waifuModeSettings" to StudioPagePreviewTarget.Page(Screen.WaifuModeSettings),
    "page.customEmojiManagement" to StudioPagePreviewTarget.Page(Screen.CustomEmojiManagement),
    "page.tagMarket" to StudioPagePreviewTarget.Page(Screen.TagMarket),
    "page.modelPromptsSettings" to StudioPagePreviewTarget.Page(Screen.ModelPromptsSettings),
    "page.functionalConfig" to StudioPagePreviewTarget.Page(Screen.FunctionalConfig),
    "page.themeSettings" to StudioPagePreviewTarget.Page(Screen.ThemeSettings),
    "page.globalDisplaySettings" to StudioPagePreviewTarget.Page(Screen.GlobalDisplaySettings),
    "page.dataStorageManagement" to StudioPagePreviewTarget.Page(Screen.DataStorageManagement),
    "page.localModelStorage" to StudioPagePreviewTarget.Page(Screen.LocalModelStorage),
    "page.linuxEnvironmentStorage" to StudioPagePreviewTarget.Page(Screen.LinuxEnvironmentStorage),
    "page.workspaceMediaStorage" to StudioPagePreviewTarget.Page(Screen.WorkspaceMediaStorage),
    "page.chatHistoryStorage" to StudioPagePreviewTarget.Page(Screen.ChatHistoryStorage),
    "page.memoryLibraryStorage" to StudioPagePreviewTarget.Page(Screen.MemoryLibraryStorage),
    "page.backupExportStorage" to StudioPagePreviewTarget.Page(Screen.BackupExportStorage),
    "page.configurationResourceStorage" to StudioPagePreviewTarget.Page(Screen.ConfigurationResourceStorage),
    "page.chatHistorySettings" to StudioPagePreviewTarget.Page(Screen.ChatHistorySettings),
    "page.chatBackupSettings" to StudioPagePreviewTarget.Page(Screen.ChatBackupSettings),
    "page.languageSettings" to StudioPagePreviewTarget.Page(Screen.LanguageSettings),
    "page.tokenUsageStatistics" to StudioPagePreviewTarget.Page(Screen.TokenUsageStatistics),
    "page.performanceMonitor" to StudioPagePreviewTarget.Page(Screen.PerformanceMonitor),
    "page.contextSummarySettings" to StudioPagePreviewTarget.Page(Screen.ContextSummarySettings),
    "page.toolPkgComposeDsl" to StudioPagePreviewTarget.Plugin,
    "page.toolPkgPluginConfig" to StudioPagePreviewTarget.Choose(Screen.Packages),
    "page.terminal" to StudioPagePreviewTarget.Page(Screen.Terminal),
    "page.terminalSetup" to StudioPagePreviewTarget.Page(Screen.TerminalSetup),
    "page.terminalAutoConfig" to StudioPagePreviewTarget.Page(Screen.TerminalAutoConfig),
    "page.uIDebugger" to StudioPagePreviewTarget.Page(Screen.UIDebugger),
    "page.logcat" to StudioPagePreviewTarget.Page(Screen.Logcat),
    "page.markdownDemo" to StudioPagePreviewTarget.Page(Screen.MarkdownDemo),
    "page.speechToText" to StudioPagePreviewTarget.Page(Screen.SpeechToText),
    "page.defaultAssistantGuide" to StudioPagePreviewTarget.Page(Screen.DefaultAssistantGuide),
    "page.htmlPackager" to StudioPagePreviewTarget.Page(Screen.HtmlPackager),
)

/** 页面对象仅在本次工作室会话中使用，不进入主题工程、脚本上下文或导出文件。 */
internal class StudioPagePreviewState {
    private val selected = mutableStateMapOf<String, Screen>()
    var selectingPage by mutableStateOf<String?>(null)
        private set

    fun screen(pageId: String): Screen? = when (val target = ThemeStudioPagePreviewTargets.getValue(pageId)) {
        is StudioPagePreviewTarget.Page -> target.screen
        is StudioPagePreviewTarget.Choose, StudioPagePreviewTarget.Plugin -> selected[pageId]
    }

    fun needsObject(pageId: String): Boolean = ThemeStudioPagePreviewTargets.getValue(pageId) !is StudioPagePreviewTarget.Page

    fun choose(pageId: String) {
        require(needsObject(pageId))
        selectingPage = pageId
    }

    fun select(pageId: String, screen: Screen) {
        require(needsObject(pageId) && screen.themePageId == pageId) { "Preview object does not match the selected page" }
        selected[pageId] = screen
        selectingPage = null
    }

    fun closePicker() { selectingPage = null }
}
