package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateCatalog

/** 文件名与复合后缀使用 TextMate 统一目录，未知格式明确作为纯文本。 */
object LanguageDetector {
    fun detectLanguage(fileName: String): String = TextMateCatalog.detectLanguage(fileName)
}
