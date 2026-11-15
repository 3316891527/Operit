package com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 编辑器主题定义
 */
data class EditorTheme(
    // 基础颜色
    val background: Color,
    val textColor: Color,
    val cursorColor: Color,
    val selectionColor: Color,
    // 搜索颜色由编辑器主题提供，画布中不再固定颜色。
    val searchMatchColor: Color,
    val activeSearchMatchColor: Color,

    // 语法高亮颜色
    val keywordColor: Color,
    val typeColor: Color,
    val stringColor: Color,
    val commentColor: Color,
    val numberColor: Color,
    val attributeColor: Color,
    val selectorColor: Color,
    val processingColor: Color,
    
    // Markdown特定颜色
    val headingColor: Color,
    val quoteColor: Color,
    val listItemColor: Color,
    val codeColor: Color,
    
    // 行号
    val gutterBackground: Color,
    val gutterBorder: Color,
    val lineNumberColor: Color,
    
    // 字体大小
    val fontSize: TextUnit
)

/**
 * 暗色主题
 */
val DarkTheme = EditorTheme(
    background = Color(0xFF1E1E1E),
    textColor = Color(0xFFD4D4D4),
    cursorColor = Color(0xFFAEAFAD),
    selectionColor = Color(0x80264F78),
    searchMatchColor = Color(0x66FFC107),
    activeSearchMatchColor = Color(0x99FF8A65),

    keywordColor = Color(0xFF6CB6FF),
    typeColor = Color(0xFF4EC9B0),
    stringColor = Color(0xFF6A8759), // 深色字符串使用经典绿色。
    commentColor = Color(0xFF9E9E9E),
    numberColor = Color(0xFFB5CEA8),
    attributeColor = Color(0xFF9CDCFE),
    selectorColor = Color(0xFFD7BA7D),
    processingColor = Color(0xFFC586C0),
    
    headingColor = Color(0xFF569CD6),
    quoteColor = Color(0xFF608B4E),
    listItemColor = Color(0xFFD7BA7D),
    codeColor = Color(0xFFDCDCAA),
    
    gutterBackground = Color(0xFF2A2D2E),
    gutterBorder = Color(0xFF3A3D41),
    lineNumberColor = Color(0xFF858585),
    
    fontSize = 14.sp
)

/** Atom One Light 风格的浅色语法配色，基础界面颜色继续跟随应用主题。 */
val LightTheme = EditorTheme(
    background = Color(0xFFFFFFFF),
    textColor = Color(0xFF1F2328),
    cursorColor = Color(0xFF1F2328),
    selectionColor = Color(0x66ADD6FF),
    searchMatchColor = Color(0x66FFD54F),
    activeSearchMatchColor = Color(0x88FFAB91),

    keywordColor = Color(0xFFA626A4),
    typeColor = Color(0xFFC18401),
    stringColor = Color(0xFF50A14F),
    // 注释选用 One Light 较深的灰色，改善手机浅色背景下的辨识度。
    commentColor = Color(0xFF696C77),
    numberColor = Color(0xFF986801),
    attributeColor = Color(0xFFE45649),
    selectorColor = Color(0xFF4078F2),
    processingColor = Color(0xFF0184BC),
    
    headingColor = Color(0xFFE45649),
    quoteColor = Color(0xFF986801),
    listItemColor = Color(0xFFE45649),
    // 画布以此字段绘制函数，与 Diff 使用的 selectorColor 保持相同蓝色。
    codeColor = Color(0xFF4078F2),
    
    gutterBackground = Color(0xFFF8F8F8),
    gutterBorder = Color(0xFFE5E5E5),
    lineNumberColor = Color(0xFF8A8F98),
    
    fontSize = 14.sp
)

/** 深色使用 VS Code 经典底色，浅色使用应用配色；应用内切换主题立即同步。 */
@Composable
fun currentEditorTheme(fontSize: Float): EditorTheme {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() <= 0.5f
    val palette = if (dark) DarkTheme else LightTheme
    return palette.copy(background = if (dark) Color(0xFF1E1E1E) else scheme.surface, textColor = scheme.onSurface,
        gutterBackground = if (dark) Color(0xFF1E1E1E) else scheme.surfaceContainerLow, gutterBorder = scheme.outlineVariant,
        lineNumberColor = scheme.onSurfaceVariant, cursorColor = scheme.primary,
        selectionColor = scheme.primary.copy(alpha = 0.25f), searchMatchColor = scheme.tertiary.copy(alpha = 0.22f),
        activeSearchMatchColor = scheme.tertiary.copy(alpha = 0.42f), fontSize = fontSize.sp)
}
