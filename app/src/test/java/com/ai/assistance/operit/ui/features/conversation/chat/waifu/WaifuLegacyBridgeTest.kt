package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import org.junit.Assert.assertEquals
import org.junit.Test

class WaifuLegacyBridgeTest {
    @Test
    fun installedJavaBridgeScriptsCanStillResolveAndCallThePublishedCleaner() {
        val legacyClass = Class.forName("com.ai.assistance.operit.util.WaifuMessageProcessor")
        val instance = legacyClass.getField("INSTANCE").get(null)
        val clean = legacyClass.getMethod("cleanContentForWaifu", String::class.java)
        assertEquals("正文。", clean.invoke(instance, "<think>思考</think><tool name=\"x\">参数</tool>正文。"))
    }
}