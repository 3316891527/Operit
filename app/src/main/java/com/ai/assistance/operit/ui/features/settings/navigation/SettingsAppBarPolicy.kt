package com.ai.assistance.operit.ui.features.settings.navigation

import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.screens.Screen

/** 普通设置页使用主题实色；主题页及其既有语言路由保留背景预览。离线资源由独立 Activity 管理。 */
internal fun Screen.usesOpaqueSettingsAppBar(): Boolean =
    navItem == NavItem.Settings && this != Screen.ThemeSettings && this != Screen.LanguageSettings
