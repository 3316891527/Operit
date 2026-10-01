package com.ai.assistance.operit.core.config

import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalPromptsWaifuTest {
    @Test
    fun waifuEmotionRuleExplainsXmlRendering() {
        val prompt = FunctionalPrompts.waifuEmotionRule("happy, custom")

        assertTrue(prompt.contains("<emotion>类别</emotion>"))
        assertTrue(prompt.contains("<emotion>happy</emotion>"))
        assertTrue(prompt.contains("客户端会把这个标签替换"))
        assertTrue(prompt.contains("不要输出图片 URL"))
    }

    @Test
    fun waifuEmotionRuleSupportsEnglish() {
        val prompt = FunctionalPrompts.waifuEmotionRule("happy, custom", useEnglish = true)

        assertTrue(prompt.contains("<emotion>category</emotion>"))
        assertTrue(prompt.contains("The app replaces this tag"))
        assertTrue(prompt.contains("Do not output image URLs"))
    }
}
