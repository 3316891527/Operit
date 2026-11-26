package com.ai.assistance.operit.ui.theme.script

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ai.assistance.operit.ui.theme.LocalIsThemePagePreview

/** 系统只支持浅色或深色图标；未指定前景时沿用原来的主题明暗规则。 */
internal fun themeStatusBarDarkIcons(contentColor: Color?, darkTheme: Boolean): Boolean =
    if (contentColor == null) !darkTheme else contentColor.luminance() < 0.5f

/** 与预览共用背景绘制；图标由系统或预览样例独立绘制，不受背景透明度影响。 */
@Composable
internal fun ThemeStatusBarBackground(modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds()) {
        Box(Modifier.matchParentSize().themeScriptDecoration("statusBar", drawStroke = false, drawElevation = false))
    }
}

/** 在根画布最后叠绘，避免页面顶栏、工作区或抽屉覆盖系统状态栏背景。 */
@Composable
internal fun ThemeSystemStatusBar(darkTheme: Boolean) {
    if (LocalIsThemePagePreview.current) return
    val surface = themeSurface("statusBar")
    val darkIcons = themeStatusBarDarkIcons(surface?.contentColor?.let(::themeColor), darkTheme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = darkIcons
        }
    }
    if (surface != null) {
        ThemeStatusBarBackground(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
    }
}
