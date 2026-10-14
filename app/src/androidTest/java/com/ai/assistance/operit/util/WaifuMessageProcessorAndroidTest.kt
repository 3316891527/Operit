package com.ai.assistance.operit.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 使用设备上的 ICU 正则引擎，覆盖 JVM 正则实现无法暴露的占位符转义错误。 */
@RunWith(AndroidJUnit4::class)
class WaifuMessageProcessorAndroidTest {
    @Test
    fun ordinaryText_doesNotRequireListMarkersToCompileThePattern() {
        assertEquals(listOf("你好。"), WaifuMessageProcessor.splitPlainTextIntoSentences("你好。", false))
    }

    @Test
    fun orderedLists_preserveNumbersAndDecimals() {
        assertEquals(
            listOf("1. 数值 1.5。", "12. 第二项。"),
            WaifuMessageProcessor.splitPlainTextIntoSentences("1. 数值 1.5。\n12. 第二项。", false),
        )
    }

    @Test
    fun placeholderLiterals_areNotReplacedAsGeneratedMarkers() {
        val literal = "{WAIFULISTDOT:99} {WAIFULISTDOT:0}"
        assertEquals(
            listOf("$literal。", "1. 事项。"),
            WaifuMessageProcessor.splitPlainTextIntoSentences("$literal。1. 事项。", false),
        )
    }
}