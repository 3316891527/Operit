package com.ai.assistance.operit.ui.features.chat.webview.workspace.links

import com.ai.assistance.operit.core.config.links.WorkspaceFileLinkGuidelines
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceFileLinkGuidelinesTest {
    @Test
    fun chineseExamplesRoundTripThroughTheFileParser() {
        assertExamples(useEnglish = false)
    }

    @Test
    fun englishExamplesRoundTripThroughTheFileParser() {
        assertExamples(useEnglish = true)
    }

    @Test
    fun encodedCharactersAndRepositoryNamesPreserveTheActualFile() {
        assertEquals(
            WorkspaceFileLink("/mnt/中文 文件(1)#?%.kt", "linux", 12),
            parseWorkspaceFileLink(
                "file:///mnt/中文%20文件%281%29%23%3F%25.kt?environment=linux#L12"
            )
        )
        assertEquals(
            WorkspaceFileLink("/src/main.kt", "repo:文档 & 示例+仓库", 12),
            parseWorkspaceFileLink(
                "file:///src/main.kt?environment=repo%3A文档%20%26%20示例%2B仓库#L12"
            )
        )
    }

    private fun assertExamples(useEnglish: Boolean) {
        // 直接提取模型实际收到的示例，防止提示词与链接解析支持的格式脱节。
        val links = Regex("""\[[^\]]+]\((file://[^)]+)\)""")
            .findAll(WorkspaceFileLinkGuidelines.forLanguage(useEnglish))
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            listOf(
                WorkspaceFileLink("/sdcard/Download/report.md", "android"),
                WorkspaceFileLink("/home/user/project/main.kt", "linux", 12),
                WorkspaceFileLink("/src/main.kt", "repo:demo", 12),
                WorkspaceFileLink("/sdcard/Download/notes (1)#?%.md", "android")
            ),
            links.map(::parseWorkspaceFileLink)
        )
    }
}
