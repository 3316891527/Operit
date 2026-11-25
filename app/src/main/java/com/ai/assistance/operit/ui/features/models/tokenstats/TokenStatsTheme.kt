package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 直接对应已评审 HTML 设计稿的颜色变量，避免应用主色和壁纸改变稿件外观。 */
internal data class TokenStatsPalette(
    val background: Color,
    val panel: Color,
    val panelAlt: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentBackground: Color,
    val teal: Color,
    val violet: Color,
    val amber: Color,
    val red: Color,
)

internal val LocalTokenStatsPalette = staticCompositionLocalOf<TokenStatsPalette> {
    error("Token statistics palette is missing")
}

@Composable
internal fun TokenStatsTheme(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val p = if (dark) TokenStatsPalette(
        Color(0xFF111416), Color(0xFF1B1F23), Color(0xFF23282D), Color(0xFF30373E),
        Color(0xFFEFF1F1), Color(0xFF9CA6AF), Color(0xFF707C86), Color(0xFFB7C5FF),
        Color(0xFF303C61), Color(0xFF81CFC3), Color(0xFFC4ABEF), Color(0xFFE4BD7E), Color(0xFFEDA6A8),
    ) else TokenStatsPalette(
        Color(0xFFF2F4F7), Color.White, Color(0xFFF4F6F9), Color(0xFFE1E6ED),
        Color(0xFF202934), Color(0xFF647180), Color(0xFF7A8796), Color(0xFF455FC5),
        Color(0xFFE8EDFF), Color(0xFF2B9385), Color(0xFF9470C9), Color(0xFFAD7827), Color(0xFFC0666A),
    )
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme.copy(
        primary = p.accent, onPrimary = if (dark) Color(0xFF162344) else Color.White,
        primaryContainer = p.accentBackground, onPrimaryContainer = p.accent,
        background = p.background, onBackground = p.ink, surface = p.panel, onSurface = p.ink,
        surfaceVariant = p.panelAlt, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line,
        surfaceContainer = p.panel, surfaceContainerHigh = p.panelAlt, surfaceContainerHighest = p.panelAlt,
        surfaceContainerLow = p.panel, surfaceContainerLowest = p.background, surfaceTint = Color.Transparent,
        error = p.red,
    ), typography = Typography()) {
        // 仅给统计页提供设计稿环境，现有价格和日期组件仍可复用相同的颜色接口。
        CompositionLocalProvider(LocalTokenStatsPalette provides p, LocalTokenStatsColors provides tokenStatsColors(), LocalMinimumInteractiveComponentSize provides 0.dp) {
            content()
        }
    }
}

@Composable
internal fun StatsText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 12,
    color: Color = LocalTokenStatsPalette.current.ink,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    mono: Boolean = false,
) {
    Text(text, modifier = modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = size.sp, lineHeight = (size * 1.5f).sp,
            fontWeight = weight, fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
            letterSpacing = 0.sp))
}

@Composable
internal fun StatsPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalTokenStatsPalette.current
    Surface(modifier = modifier.fillMaxWidth(), color = p.panel, contentColor = p.ink,
        border = BorderStroke(1.dp, p.line), shape = RoundedCornerShape(20.dp), tonalElevation = 0.dp) {
        Column(Modifier.padding(if (LocalConfiguration.current.screenWidthDp < 370) 17.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun StatsDivider() {
    HorizontalDivider(color = LocalTokenStatsPalette.current.line, thickness = 1.dp)
}

@Composable
internal fun StatsAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalTokenStatsPalette.current
    Box(modifier.clip(RoundedCornerShape(7.dp)).clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 34.dp).padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        StatsText(text, color = p.accent.copy(alpha = if (enabled) 1f else .33f), size = 11, maxLines = 1)
    }
}

@Composable
internal fun StatsSelect(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalTokenStatsPalette.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, p.line, RoundedCornerShape(8.dp))
            .clickable { expanded = true }.heightIn(min = 35.dp).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatsText(label, Modifier.weight(1f, fill = false), size = 11, color = p.muted, maxLines = 1)
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = p.muted, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = p.panel, tonalElevation = 0.dp) {
            options.forEachIndexed { index, value ->
                DropdownMenuItem(text = { StatsText(value, color = if (selected == index) p.accent else p.ink) }, onClick = { expanded = false; onSelect(index) })
            }
        }
    }
}

@Composable
internal fun StatsSegment(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalTokenStatsPalette.current
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(p.background).border(1.dp, p.line, RoundedCornerShape(10.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { index, title ->
            Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if (index == selected) p.accentBackground else Color.Transparent)
                .clickable { onSelect(index) }.heightIn(min = 32.dp).padding(horizontal = 11.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                StatsText(title, size = 11, color = if (index == selected) p.accent else p.muted,
                    weight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun StatsAvatar(label: String, modifier: Modifier = Modifier) {
    val p = LocalTokenStatsPalette.current
    Box(modifier.size(31.dp).clip(RoundedCornerShape(9.dp)).background(p.accentBackground), contentAlignment = Alignment.Center) {
        StatsText(label.take(1).uppercase(), size = 12, color = p.accent, weight = FontWeight.SemiBold)
    }
}

@Composable
internal fun StatsPrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalTokenStatsPalette.current
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(p.accent.copy(alpha = if (enabled) 1f else .33f))
        .clickable(enabled = enabled, onClick = onClick).heightIn(min = 47.dp).padding(horizontal = 17.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        StatsText(text, size = 12, color = if (p.background.luminance() < .5f) Color(0xFF162344) else Color.White, weight = FontWeight.SemiBold, maxLines = 1)
    }
}
