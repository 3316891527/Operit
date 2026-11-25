package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.ThemeStatusBarBackground
import com.ai.assistance.operit.ui.theme.script.themeColor
import com.ai.assistance.operit.ui.theme.script.themeStatusBarDarkIcons
import com.ai.assistance.operit.ui.theme.script.themeSurface

/** 只编辑系统支持的明暗，复用内容色字段保存浅深模式，不提供任意系统图标染色。 */
@Composable
internal fun StudioStatusBarIconProperties(state: ThemeStudioState) {
    val color = state.surface().contentColor
    val selection = if (color == null) null else if (themeStatusBarDarkIcons(themeColor(color), false)) "dark" else "light"
    StudioSection(stringResource(R.string.studio_status_bar_icons)) {
        StudioChoices(selection, listOf(
            null to stringResource(R.string.studio_status_bar_follow_theme),
            "dark" to stringResource(R.string.studio_status_bar_dark_icons),
            "light" to stringResource(R.string.studio_status_bar_light_icons),
        )) { choice ->
            state.updateSurface { it.copy(contentColor = when (choice) {
                null -> null
                "dark" -> "#FF000000"
                "light" -> "#FFFFFFFF"
                else -> error("Unknown system status bar icon appearance")
            }) }
        }
        StudioHint(stringResource(R.string.studio_status_bar_icons_hint))
    }
}

/** 固定时间、信号和电量仅用于编辑预览，不读取设备状态，也不写真实 Window。 */
@Composable
internal fun StudioStatusBarPreview(darkTheme: Boolean) {
    val color = themeSurface("statusBar")?.contentColor?.let(::themeColor)
    val foreground = if (themeStatusBarDarkIcons(color, darkTheme)) Color.Black else Color.White
    val description = stringResource(R.string.studio_status_bar_preview_description)
    Box(Modifier.fillMaxWidth().height(28.dp)) {
        ThemeStatusBarBackground(Modifier.matchParentSize())
        Row(
            Modifier.matchParentSize().padding(horizontal = 16.dp).clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("9:41", color = foreground, style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.size(14.dp)) {
                repeat(3) { index ->
                    val height = size.height * (index + 1) / 3f
                    drawRect(foreground, Offset(size.width * index / 3f, size.height - height), Size(size.width / 5f, height))
                }
            }
            Icon(Icons.Default.Wifi, null, Modifier.size(14.dp), tint = foreground)
            Canvas(Modifier.size(16.dp)) {
                val top = size.height * 0.2f
                drawRect(foreground, Offset(0f, top), Size(size.width * 0.82f, size.height * 0.6f))
                drawRect(foreground, Offset(size.width * 0.88f, size.height * 0.35f), Size(size.width * 0.12f, size.height * 0.3f))
            }
        }
    }
}
