package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownOutlineAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun outlineUsesParsedHeadingsAndExcludesFencedCode() = runBlocking {
        val content = """
            # 一级

            ## **二级** [链接](https://example.com)

            ### 三级

            #### 四级

            ##### 五级

            ###### 六级

            ```md
            # 代码里的伪标题
            ```

            普通段落含有 # 井号。
        """.trimIndent() + "\n"
        val headings = markdownHeadings(parseMarkdownToNodes(content).map { it.toStableNode() })
        assertEquals((1..6).toList(), headings.map { it.level })
        assertEquals(listOf("一级", "二级 链接", "三级", "四级", "五级", "六级"), headings.map { it.title })
        assertEquals(headings.dropLast(1).map { it.nodeIndex }, headings.drop(1).map { it.parentIndex })
    }

    @Test fun skippedLevelsAndDuplicateTitlesKeepTheirOwnParentAndPosition() {
        val headings = markdownHeadings(listOf(header("# 同名"), header("### 深层"), header("## 同名"), header("#### 子节"), header("# 同名")))
        assertEquals(listOf(null, 0, 0, 2, null), headings.map { it.parentIndex })
        assertEquals(listOf(0, 1, 2, 3, 4), headings.map { it.nodeIndex })
        assertEquals(3, headings.count { it.title == "同名" })
    }

    @Test fun collapsingASectionHidesItsDescendantsButKeepsSiblingSections() {
        val headings = markdownHeadings(listOf(header("# A"), header("## B"), header("### C"), header("## D"), header("# E")))
        assertEquals(listOf(0, 4), visibleMarkdownHeadings(headings, setOf(0, 1)).map { it.nodeIndex })
        assertEquals(listOf(0, 1, 3, 4), visibleMarkdownHeadings(headings, setOf(1)).map { it.nodeIndex })
        assertEquals(headings, visibleMarkdownHeadings(headings, emptySet()))
    }

    @Test fun rendererPublishesRealHeadingPositionsAndRemovedHeadingsLoseTheirOffsets() {
        val outline = MarkdownOutlineState()
        val content = "# 开始\n\n" + "正文段落。\n\n".repeat(24) + "## 结束\n"
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(LocalMarkdownOutline provides outline) { StreamMarkdownRenderer(content = content) }
                }
            }
        }
        compose.waitUntil(10_000) {
            outline.headings.size == 2 && outline.offset(outline.headings.last().nodeIndex) != null
        }
        compose.runOnIdle {
            val first = outline.headings.first().nodeIndex
            val last = outline.headings.last().nodeIndex
            val lastTop = checkNotNull(outline.offset(last))
            assertTrue(lastTop > checkNotNull(outline.offset(first)))
            assertEquals(last, outline.activeAt(lastTop))
            outline.updateHeadings(emptyList())
            assertNull(outline.offset(first))
            assertNull(outline.offset(last))
        }
    }

    private fun header(text: String) = MarkdownNodeStable(MarkdownProcessorType.HEADER, text, emptyList())
}
