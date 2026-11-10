package com.ai.assistance.operit.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** 工作室复用真实页面时，只呈现会话，不消费共享输入或触发主界面的会话操作。 */
val LocalIsThemePagePreview = staticCompositionLocalOf { false }
