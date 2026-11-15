package com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.EditorTheme
import java.util.concurrent.ConcurrentHashMap

internal data class EditorResolvedHighlightStyle(
    val color: Int,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strike: Boolean,
)

/** 画布和 Diff 共用颜色解析；主题切换只重建绘制缓存，不重新分词。 */
internal class EditorHighlightRendering(private val theme: EditorTheme) {
    private val dark = theme.background.luminance() <= 0.5f
    private val cache = ConcurrentHashMap<EditorHighlightStyle, EditorResolvedHighlightStyle>()

    fun resolve(style: EditorHighlightStyle): EditorResolvedHighlightStyle = cache.getOrPut(style) {
        val configured = if (dark) style.dark else style.light
        val color = if (configured != null) {
            configured
        } else {
            when (style.colorRole) {
                "default" -> theme.textColor
                "keyword" -> theme.keywordColor
                "function" -> theme.codeColor
                "string" -> theme.stringColor
                "number" -> theme.numberColor
                "comment" -> theme.commentColor
                "type" -> theme.typeColor
                "variable" -> theme.attributeColor
                "heading" -> theme.headingColor
                "quote" -> theme.quoteColor
                "list" -> theme.listItemColor
                "processing" -> theme.processingColor
                else -> error("Unknown editor color role: ${style.colorRole}")
            }.toArgb()
        }
        EditorResolvedHighlightStyle(color, style.bold, style.italic, style.underline, style.strike)
    }
}
