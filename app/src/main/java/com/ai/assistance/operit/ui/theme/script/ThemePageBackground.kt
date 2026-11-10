package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.theme.script.hasBackgroundPaint
import com.ai.assistance.operit.data.theme.script.CHAT_PAGE_BACKGROUND_ID
import com.ai.assistance.operit.data.theme.script.pageBackgroundSource

private val LocalThemePagePaint = compositionLocalOf { false }

/** Only page content canvases become transparent; cards and native controls retain their surfaces. */
@Composable
fun themePageContainerColor(original: Color): Color = if (LocalThemePagePaint.current) Color.Transparent else original

@Composable
internal fun ThemePageBackground(pageId: String, content: @Composable () -> Unit) {
    val backgroundId = pageBackgroundSource(pageId, themeSurface(pageId), themeSurface(CHAT_PAGE_BACKGROUND_ID))
    val painted = backgroundId != null
    val preferences = com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot.current
    val globalPaint = themeSurface("background")?.hasBackgroundPaint() == true ||
        (preferences.useBackgroundImage && preferences.backgroundImageUri != null)
    CompositionLocalProvider(LocalThemePagePaint provides (painted || globalPaint)) {
        Box(Modifier.fillMaxSize()) {
            if (painted) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background)
                .themeScriptDecoration(requireNotNull(backgroundId)))
            content()
        }
    }
}

/** Same Scaffold contract, with the page's declared background visible through its content canvas. */
@Composable
fun ThemePageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(modifier = modifier, topBar = topBar, bottomBar = bottomBar, snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton, floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = themePageContainerColor(containerColor), contentColor = contentColor,
        contentWindowInsets = contentWindowInsets, content = content)
}
