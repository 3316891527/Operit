package com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatProcessingStatusTextAndroidTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun switchingBetweenResponseAndBatchToolStatusesKeepsTheSameHeight() {
        val statuses = listOf(
            "正在接收响应",
            "正在执行工具：" + List(57) { "读取文件" }.joinToString(", "),
            "正在执行工具：读取文件、编辑文件、执行终端命令、检查工作区差异、搜索项目代码",
            "正在执行工具：终端\n进度：已完成目录扫描\n正在等待下一条命令",
            "正在接收响应",
            "正在执行工具：create_file * 5, read_file * 2",
            "正在执行工具：create_file * 4, read_file * 2",
            "正在执行工具：create_file * 3, read_file",
            "正在执行工具：create_file * 2",
            "正在执行工具：create_file",
            "正在处理工具结果：create_file * 5, read_file * 2",
        )
        val message = mutableStateOf(statuses.first())
        composeRule.setContent {
            MaterialTheme {
                ChatProcessingStatusText(
                    message = message.value,
                    modifier = Modifier.width(220.dp).testTag("processing-status"),
                )
            }
        }

        val statusNode = composeRule.onNodeWithTag("processing-status")
        val initialHeight = statusNode.fetchSemanticsNode().boundsInRoot.height
        assertTrue("状态文本应具有可见高度", initialHeight > 0f)

        // 连续切换同一输入栏中的状态，覆盖重复工具、不同工具和带换行的进度回调。
        statuses.forEachIndexed { index, status ->
            composeRule.runOnIdle { message.value = status }
            statusNode.assertIsDisplayed().assertTextEquals(status)
            assertEquals(
                "状态变化不能撑高输入栏：$index",
                initialHeight,
                statusNode.fetchSemanticsNode().boundsInRoot.height,
                0.5f,
            )

            val layouts = mutableListOf<TextLayoutResult>()
            statusNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("状态只占一行：$index", 1, layout.lineCount)
            if (index in 1..3) {
                assertTrue("超长状态应显示省略号：$index", layout.isLineEllipsized(0))
            }
        }
    }
}
