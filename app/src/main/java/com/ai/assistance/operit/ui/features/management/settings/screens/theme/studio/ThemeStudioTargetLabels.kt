package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R

@Composable
internal fun studioPageLabel(id: String): String = stringResource(when (id) {
    "page.aiChat" -> R.string.studio_page_aichat
    "page.memoryBase" -> R.string.studio_page_memorybase
    "page.memorySpaceDetail" -> R.string.studio_page_memoryspacedetail
    "page.packages" -> R.string.studio_page_packages
    "page.packageDetail" -> R.string.studio_page_packagedetail
    "page.market" -> R.string.studio_page_market
    "page.marketCategory" -> R.string.studio_page_marketcategory
    "page.marketNotifications" -> R.string.studio_page_marketnotifications
    "page.builtInMarketDetail" -> R.string.studio_page_builtinmarketdetail
    "page.marketEntryDetail" -> R.string.studio_page_marketentrydetail
    "page.marketAuthor" -> R.string.studio_page_marketauthor
    "page.marketManage" -> R.string.studio_page_marketmanage
    "page.artifactPublish" -> R.string.studio_page_artifactpublish
    "page.artifactContinuePublish" -> R.string.studio_page_artifactcontinuepublish
    "page.artifactEdit" -> R.string.studio_page_artifactedit
    "page.repoPublish" -> R.string.studio_page_repopublish
    "page.repoEdit" -> R.string.studio_page_repoedit
    "page.repoPublishVersion" -> R.string.studio_page_repopublishversion
    "page.shizukuCommands" -> R.string.studio_page_shizukucommands
    "page.settings" -> R.string.studio_page_settings
    "page.gitHubAccount" -> R.string.studio_page_githubaccount
    "page.help" -> R.string.studio_page_help
    "page.about" -> R.string.studio_page_about
    "page.agreement" -> R.string.studio_page_agreement
    "page.updateHistory" -> R.string.studio_page_updatehistory
    "page.workflow" -> R.string.studio_page_workflow
    "page.workflowDetail" -> R.string.studio_page_workflowdetail
    "page.tokenConfig" -> R.string.studio_page_tokenconfig
    "page.userPreferencesSettings" -> R.string.studio_page_userpreferencessettings
    "page.toolPermission" -> R.string.studio_page_toolpermission
    "page.modelConfig" -> R.string.studio_page_modelconfig
    "page.speechServicesSettings" -> R.string.studio_page_speechservicessettings
    "page.mnnModelDownload" -> R.string.studio_page_mnnmodeldownload
    "page.personaCardGeneration" -> R.string.studio_page_personacardgeneration
    "page.waifuModeSettings" -> R.string.studio_page_waifumodesettings
    "page.customEmojiManagement" -> R.string.studio_page_customemojimanagement
    "page.tagMarket" -> R.string.studio_page_tagmarket
    "page.modelPromptsSettings" -> R.string.studio_page_modelpromptssettings
    "page.functionalConfig" -> R.string.studio_page_functionalconfig
    "page.themeSettings" -> R.string.studio_page_themesettings
    "page.globalDisplaySettings" -> R.string.studio_page_globaldisplaysettings
    "page.dataStorageManagement" -> R.string.studio_page_datastoragemanagement
    "page.localModelStorage" -> R.string.studio_page_localmodelstorage
    "page.linuxEnvironmentStorage" -> R.string.studio_page_linuxenvironmentstorage
    "page.workspaceMediaStorage" -> R.string.studio_page_workspacemediastorage
    "page.chatHistoryStorage" -> R.string.studio_page_chathistorystorage
    "page.memoryLibraryStorage" -> R.string.studio_page_memorylibrarystorage
    "page.backupExportStorage" -> R.string.studio_page_backupexportstorage
    "page.configurationResourceStorage" -> R.string.studio_page_configurationresourcestorage
    "page.chatHistorySettings" -> R.string.studio_page_chathistorysettings
    "page.chatBackupSettings" -> R.string.studio_page_chatbackupsettings
    "page.languageSettings" -> R.string.studio_page_languagesettings
    "page.tokenUsageStatistics" -> R.string.studio_page_tokenusagestatistics
    "page.performanceMonitor" -> R.string.studio_page_performancemonitor
    "page.contextSummarySettings" -> R.string.studio_page_contextsummarysettings
    "page.toolPkgComposeDsl" -> R.string.studio_page_toolpkgcomposedsl
    "page.toolPkgPluginConfig" -> R.string.studio_page_toolpkgpluginconfig
    "page.terminal" -> R.string.studio_page_terminal
    "page.terminalSetup" -> R.string.studio_page_terminalsetup
    "page.terminalAutoConfig" -> R.string.studio_page_terminalautoconfig
    "page.uIDebugger" -> R.string.studio_page_uidebugger
    "page.logcat" -> R.string.studio_page_logcat
    "page.markdownDemo" -> R.string.studio_page_markdowndemo
    "page.speechToText" -> R.string.studio_page_speechtotext
    "page.defaultAssistantGuide" -> R.string.studio_page_defaultassistantguide
    "page.htmlPackager" -> R.string.studio_page_htmlpackager
    else -> error("Unknown page background: $id")
})

@Composable
internal fun studioTextTargetLabel(id: String): String = stringResource(when (id) {
    "global" -> R.string.studio_text_global
    "displayLarge" -> R.string.studio_text_displaylarge
    "displayMedium" -> R.string.studio_text_displaymedium
    "displaySmall" -> R.string.studio_text_displaysmall
    "headlineLarge" -> R.string.studio_text_headlinelarge
    "headlineMedium" -> R.string.studio_text_headlinemedium
    "headlineSmall" -> R.string.studio_text_headlinesmall
    "titleLarge" -> R.string.studio_text_titlelarge
    "titleMedium" -> R.string.studio_text_titlemedium
    "titleSmall" -> R.string.studio_text_titlesmall
    "bodyLarge" -> R.string.studio_text_bodylarge
    "bodyMedium" -> R.string.studio_text_bodymedium
    "bodySmall" -> R.string.studio_text_bodysmall
    "labelLarge" -> R.string.studio_text_labellarge
    "labelMedium" -> R.string.studio_text_labelmedium
    "labelSmall" -> R.string.studio_text_labelsmall
    "chat.modelName" -> R.string.studio_text_chat_modelname
    "chat.title" -> R.string.studio_text_chat_title
    "chat.tokenCount" -> R.string.studio_text_chat_tokencount
    "navigation.title" -> R.string.studio_text_navigation_title
    "package.name" -> R.string.studio_text_package_name
    "package.description" -> R.string.studio_text_package_description
    "package.version" -> R.string.studio_text_package_version
    "settings.title" -> R.string.studio_text_settings_title
    "settings.description" -> R.string.studio_text_settings_description
    else -> error("Unknown text style target: $id")
})
