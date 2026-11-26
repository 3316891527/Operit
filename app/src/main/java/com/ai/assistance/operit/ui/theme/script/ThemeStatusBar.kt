package com.ai.assistance.operit.ui.theme.script

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ai.assistance.operit.ui.theme.LocalIsThemePagePreview

/** 跟随主题时浅色用黑色图标，深色用白色图标。 */
internal fun themeStatusBarDarkIcons(contentColor: Color?, darkTheme: Boolean): Boolean =
    if (contentColor == null) !darkTheme else contentColor.luminance() < 0.5f

/** 绘制完整的状态栏背景，时间和图标由窗口或预览单独显示。 */
@Composable
internal fun ThemeStatusBarBackground(modifier: Modifier) {
    Spacer(modifier.clipToBounds().themeScriptDecoration("statusBar", drawStroke = false, drawElevation = false))
}

/** 状态栏留白与正文保持原有测量，仅在绘制时隔开自定义状态栏和页面。 */
@Composable
internal fun ThemeSystemStatusBarLayout(darkTheme: Boolean, content: @Composable () -> Unit) {
    if (LocalIsThemePagePreview.current) {
        content()
        return
    }
    val state = LocalThemeScriptState.current
    val surface = (state as? ThemeScriptState.Ready)?.appearance?.surfaces?.get("statusBar")
    val darkIcons = themeStatusBarDarkIcons(surface?.contentColor?.let(::themeColor), darkTheme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = darkIcons
        }
    }
    val insets = WindowInsets.statusBars
    val pageModifier = if (surface == null) Modifier else Modifier.drawWithContent {
        // 顶栏、工作区和抽屉保留原布局，透明的状态栏区域直接露出主题根背景。
        val statusBarHeight = insets.getTop(this).toFloat().coerceAtMost(size.height)
        clipRect(top = statusBarHeight) { this@drawWithContent.drawContent() }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(pageModifier)) { content() }
        if (surface != null) {
            ThemeStatusBarBackground(Modifier.fillMaxWidth().windowInsetsTopHeight(insets))
        }
    }
}
