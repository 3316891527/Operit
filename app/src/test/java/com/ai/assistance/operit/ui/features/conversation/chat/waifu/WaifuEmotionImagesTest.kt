package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WaifuEmotionImagesTest {
    @Test
    fun onlyCompleteCategoryDirectivesAreAccepted() {
        assertEquals("miss_you", waifuEmotionCategory("\n<emotion>\n miss_you \n</emotion>\t"))
        for (invalid in listOf(
            "<emotion>happy", "<emotion>happy</emot", "<emotion/>", "<emotion></emotion>",
            "<emotion>happy-sad</emotion>", "<emotion>Happy</emotion>",
            "<emotion name=\"happy\">happy</emotion>", "<emotion><b>happy</b></emotion>",
            "\\<emotion>happy</emotion>", "文字<emotion>happy</emotion>",
        )) {
            assertNull(invalid, waifuEmotionCategory(invalid))
        }
    }

    @Test
    fun snapshotsReusePerCategorySelectionsAcrossRecalculationAndTruncation() {
        var selections = 0
        val images = WaifuEmotionImages(
            mapOf("happy" to listOf("file:///a.png", "file:///b.png"), "sad" to listOf("file:///c.gif")),
            chooseIndex = { size -> (selections++ / 2) % size },
        )
        val first = images.snapshot()
        assertEquals("![](file:///a.png)", first.render("happy"))
        assertEquals("![](file:///c.gif)", first.render("sad"))
        assertEquals("![](file:///b.png)", first.render("happy"))

        val shortened = images.snapshot()
        assertEquals("![](file:///a.png)", shortened.render("happy"))
        val retry = images.snapshot()
        assertEquals("![](file:///a.png)", retry.render("happy"))
        assertEquals("![](file:///b.png)", retry.render("happy"))
        assertEquals("![](file:///c.gif)", retry.render("sad"))
        assertEquals(3, selections)
    }

    @Test
    fun selectionDiagnosticsReportOnlyNewOccurrencesWithTheirResolvedResources() {
        val reported = mutableListOf<Pair<String, String>>()
        val images = WaifuEmotionImages(
            mapOf("test" to listOf("file:///fixture-a.png", "file:///fixture-b.png")),
            chooseIndex = { reported.size % it },
            onSelection = { category, resource -> reported += category to resource },
        )

        assertEquals("![](file:///fixture-a.png)", images.snapshot().render("test"))
        val rebuilt = images.snapshot()
        assertEquals("![](file:///fixture-a.png)", rebuilt.render("test"))
        assertEquals("![](file:///fixture-b.png)", rebuilt.render("test"))
        assertEquals("![](file:///fixture-a.png)", images.snapshot().render("test"))
        assertNull(images.snapshot().render("unknown"))
        assertEquals(listOf("test" to "file:///fixture-a.png", "test" to "file:///fixture-b.png"), reported)
    }

    @Test
    fun unavailableCategoriesDoNotSelectAnImage() {
        val images = WaifuEmotionImages(mapOf("empty" to emptyList()), chooseIndex = { error("没有素材时不选图") })
        assertFalse(images.hasCategory("missing"))
        assertFalse(images.hasCategory("empty"))
        val snapshot = images.snapshot()
        assertNull(snapshot.render("missing"))
        assertNull(snapshot.render("empty"))
    }

    @Test
    fun snapshotKeepsOnlyCompleteEmotionBlocksWithAvailableImages() {
        val images = WaifuEmotionImages(mapOf("happy" to listOf("file:///a.png")))
        for ((xml, keep) in listOf(
            "<emotion>happy</emotion>" to true,
            "<emotion>sad</emotion>" to false,
            "<emotion>happy</emot" to false,
        )) {
            val prefix = "前文。"
            val full = prefix + xml
            val parts = intArrayOf(
                MarkdownProcessorType.PLAIN_TEXT.ordinal, 0, prefix.length,
                MarkdownProcessorType.XML_BLOCK.ordinal, prefix.length, full.length,
            )
            assertEquals(
                if (keep) full else prefix,
                WaifuMessageProcessor.buildRenderableSnapshot(
                    full, false, parts, includeOpenXmlPreview = true, emotionImages = images,
                ),
            )
            assertEquals(
                prefix,
                WaifuMessageProcessor.buildRenderableSnapshot(full, true, parts, includeOpenXmlPreview = true),
            )
        }
    }
}
