package com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTokenKind
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.HighlightSnapshot
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.TextMateTestResources
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTextMateHighlighter
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTextSnapshot
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorLineIndex

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.DarkTheme
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.LightTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditorHighlightConfigurationTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun configuration(vararg files: Pair<String, String>): EditorHighlightConfiguration {
        val parser = EditorHighlightConfigParser()
        val parsed = files.associate { (id, source) -> id to parser.parse(id, source) }
        return EditorHighlightConfiguration(parsed["_global"] ?: EditorLanguageHighlight("_global"), parsed.filterKeys { it != "_global" })
            .also { it.validate() }
    }

    private fun highlight(configuration: EditorHighlightConfiguration, language: String, text: String): HighlightSnapshot =
        TextMateTestResources(configuration).use { resources ->
            checkNotNull(EditorTextMateHighlighter.highlight(EditorTextSnapshot(EditorLineIndex.fromText(text), 0, Any()), resources.rules(language)))
        }

    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected invalid configuration") } catch (_: IllegalArgumentException) { }
    }

    @Test fun arbitraryUnicodeStyleNamesAndCaptureGroupsKeepUtf16Offsets() {
        val config = configuration("text" to """{
            "version":1,"mode":"replace","styles":{"声明名":{"light":"#123456","dark":"#ABCDEF"}},
            "rules":[{"match":"\\b(class)\\s+([\\p{L}_][\\p{L}\\p{N}_]*)","captures":{"1":"keyword","2":"声明名"}}]
        }""")
        val text = "👋 class 中文"
        val result = highlight(config, "text", text)
        assertEquals("keyword", result.styleAt(text.indexOf("class")).colorRole)
        for (offset in text.indexOf("中文") until text.length) assertEquals(0xFF123456.toInt(), result.styleAt(offset).light)
        assertEquals(EditorHighlightStyle.Default, result.styleAt(0))
        assertEquals(EditorHighlightStyle.Default, result.styleAt(1))
    }

    @Test fun globalAndLanguagePropertiesMergeIncludingExplicitFalse() {
        val config = configuration("_global" to """{
            "version":1,"styles":{"强调":{"extends":"keyword","light":"#123456","bold":true,"italic":true}}
        }""", "kotlin" to """{
            "version":1,"styles":{"强调":{"dark":"#FEDCBA","bold":false}},
            "scopes":[{"scope":"entity.name.function","style":"强调"}]
        }""")
        val style = EditorStyleResolver(config, "kotlin").resolve(listOf("source.kotlin", "entity.name.function.kotlin"))
        assertEquals(0xFF123456.toInt(), style.light)
        assertEquals(0xFFFEDCBA.toInt(), style.dark)
        assertFalse(style.bold)
        assertTrue(style.italic)
    }

    @Test fun extendedRulesRespectExistingStringAndCommentRegions() {
        val config = configuration("kotlin" to """{
            "version":1,"styles":{"加号":{"light":"#123456"}},
            "rules":[{"literals":["+"],"style":"加号"}]
        }""")
        val text = "val n = 1 + 2; val s = \"+\" // +"
        val result = highlight(config, "kotlin", text)
        assertEquals(0xFF123456.toInt(), result.styleAt(text.indexOf('+')).light)
        assertEquals("string", result.styleAt(text.indexOf('+', text.indexOf('"'))).colorRole)
        assertEquals("comment", result.styleAt(text.lastIndexOf('+')).colorRole)
    }

    @Test fun literalChoicesUseLongestMatchAndWordsKeepIdentifierBoundaries() {
        val config = configuration("text" to """{
            "version":1,"mode":"replace","styles":{"符号":{"light":"#123456"}},
            "rules":[{"literals":["+","++"],"style":"符号"},{"words":["if","as?"],"ignoreCase":true,"style":"keyword"}]
        }""")
        val text = "++ IF iffy as?"
        val result = highlight(config, "text", text)
        assertEquals(0xFF123456.toInt(), result.styleAt(1).light)
        assertEquals("keyword", result.styleAt(text.indexOf("IF")).colorRole)
        assertEquals(EditorHighlightStyle.Default, result.styleAt(text.indexOf("iffy")))
        assertEquals("keyword", result.styleAt(text.indexOf("as?") + 2).colorRole)
    }

    @Test fun endLastAllowsDoubledQuotesWithoutEndingTheString() {
        val config = configuration("text" to """{
            "version":1,"mode":"replace","styles":{"转义":{"light":"#123456"}},
            "rules":[{"begin":"@\"","end":"\"","style":"string","endLast":true,
                "rules":[{"literals":["\"\""],"style":"转义"}]}]
        }""")
        val text = "@\"a \"\"b\"\" c\" tail"
        val result = highlight(config, "text", text)
        assertEquals("string", result.styleAt(text.indexOf('c')).colorRole)
        assertEquals(0xFF123456.toInt(), result.styleAt(text.indexOf("\"\"")).light)
        assertEquals(EditorHighlightStyle.Default, result.styleAt(text.indexOf("tail")))
    }

    @Test fun beginCaptureBackreferencesCloseTheMatchingMultilineRegion() {
        val config = configuration("text" to """{
            "version":1,"mode":"replace","rules":[{"begin":"^(#+)","end":"^\\1$","style":"string"}]
        }""")
        val text = "##\nbody\n##\ntail"
        val result = highlight(config, "text", text)
        assertEquals("string", result.styleAt(text.indexOf("body")).colorRole)
        assertEquals(EditorHighlightStyle.Default, result.styleAt(text.indexOf("tail")))
    }

    @Test fun capturedMetacharacterAndUnicodeDelimitersCloseLiterally() {
        val config = configuration("text" to """{
            "version":1,"mode":"replace","rules":[{"begin":"^OPEN (.+)$","end":"^\\1$","style":"string"}]
        }""")
        for (marker in listOf("##", "**", "a+b?", "[x](y){2}|.", "${'$'}\\", "中文👋")) {
            val text = "OPEN $marker\nbody\n$marker\ntail"
            val result = highlight(config, "text", text)
            assertEquals(marker, "string", result.styleAt(text.indexOf("body")).colorRole)
            assertEquals(marker, EditorHighlightStyle.Default, result.styleAt(text.indexOf("tail")))
        }
    }

    @Test fun capturedDelimiterDoesNotBecomeARegexQuantifier() {
        val config = configuration("text" to """{
            "version":1,"mode":"replace","rules":[{"begin":"^OPEN (.+)$","end":"^\\1$","style":"string"}]
        }""")
        val text = "OPEN a+b\naaab\nstill inside\na+b\ntail"
        val result = highlight(config, "text", text)
        assertEquals("string", result.styleAt(text.indexOf("still inside")).colorRole)
        assertEquals(EditorHighlightStyle.Default, result.styleAt(text.indexOf("tail")))
    }

    @Test fun emptyBeginCaptureProducesAnEmptyEndExpression() {
        val config = configuration("text" to """{
            "version":1,"mode":"replace","rules":[{"begin":"^OPEN ()$","end":"\\1","style":"string"}]
        }""")
        val text = "OPEN \ntail"
        assertEquals(EditorHighlightStyle.Default, highlight(config, "text", text).styleAt(text.indexOf("tail")))
    }

    @Test fun markdownBoldClosesBeforeFollowingPlainText() {
        val text = "plain **bold** tail"
        val result = highlight(EditorHighlightConfiguration.Empty, "markdown", text)
        assertTrue(result.styleAt(text.indexOf("bold")).bold)
        assertEquals(EditorHighlightStyle.Default, result.styleAt(text.indexOf("tail")))
    }

    @Test fun markdownStrikePreservesMarkerLengthEscapesAndWordBoundaries() {
        for (text in listOf("plain ~~gone~~ tail", "plain ~~~gone~~~ tail", "plain ~~**gone**~~ tail")) {
            val result = highlight(EditorHighlightConfiguration.Empty, "markdown", text)
            assertTrue(text, result.styleAt(text.indexOf("gone")).strike)
            assertEquals(text, EditorHighlightStyle.Default, result.styleAt(text.indexOf("tail")))
        }
        for (text in listOf("plain ~gone~ tail", "plain \\~~gone~~ tail", "plain ~~gone_~~word")) {
            assertFalse(text, highlight(EditorHighlightConfiguration.Empty, "markdown", text).styleAt(text.indexOf("gone")).strike)
        }
    }

    @Test fun recursionThroughAConsumingRegionIsAllowed() {
        val config = configuration("text" to """{
            "version":1,"mode":"replace","rules":[
                {"begin":"\\{","end":"\\}","rules":[{"include":"${'$'}self"}]},
                {"words":["mark"],"style":"keyword"}]
        }""")
        val text = "{ { mark } }"
        assertEquals("keyword", highlight(config, "text", text).styleAt(text.indexOf("mark")).colorRole)
    }

    @Test fun includeOnlyAndCrossLanguageCyclesAreRejected() {
        rejects { configuration("text" to """{"version":1,"mode":"replace","rules":[{"include":"loop"}],"definitions":{"loop":[{"include":"loop"}]}}""") }
        rejects { configuration("one" to """{"version":1,"mode":"replace","rules":[{"language":"two"}]}""",
            "two" to """{"version":1,"mode":"replace","rules":[{"language":"one"}]}""") }
    }

    @Test fun styleCyclesAndUnknownReferencesAreRejected() {
        rejects { configuration("text" to """{"version":1,"styles":{"a":{"extends":"b"},"b":{"extends":"a"}}}""") }
        rejects { configuration("text" to """{"version":1,"rules":[{"words":["x"],"style":"missing"}]}""") }
        rejects { configuration("text" to """{"version":1,"rules":[{"include":"missing"}]}""") }
    }

    @Test fun unsupportedFieldsDuplicateKeysAndNonJsonSyntaxAreRejected() {
        val parser = EditorHighlightConfigParser()
        rejects { parser.parse("text", """{"version":1,"version":1}""") }
        rejects { parser.parse("text", """{"version":1,"parseColor":true}""") }
        rejects { parser.parse("text", """{version:1}""") }
        rejects { parser.parse("text", """{"version":1,}""") }
        rejects { parser.parse("text", """{"version":2}""") }
    }

    @Test fun invalidPatternsCapturesAndEmptyMatchersAreRejected() {
        val parser = EditorHighlightConfigParser()
        rejects { parser.parse("text", """{"version":1,"rules":[{"match":"("}]}""") }
        rejects { parser.parse("text", """{"version":1,"rules":[{"match":"a*"}]}""") }
        rejects { parser.parse("text", """{"version":1,"rules":[{"match":"(x)","captures":{"2":"keyword"}}]}""") }
        rejects { parser.parse("text", """{"version":1,"rules":[{"begin":"x","end":"\\1"}]}""") }
    }

    @Test fun customAssociationsPreferExactNamesAndLongestSuffixes() {
        val config = configuration("one" to """{"version":1,"mode":"replace","extensions":[".demo"],"rules":[{"words":["x"]}]}""",
            "two" to """{"version":1,"mode":"replace","extensions":[".test.demo"],"fileNames":["Special.Demo"],"rules":[{"words":["x"]}]}""")
        assertEquals("two", config.languageFor("text", "/path/FILE.TEST.DEMO"))
        assertEquals("two", config.languageFor("text", "Special.Demo"))
        assertEquals("one", config.languageFor("text", "file.demo"))
    }

    @Test fun conflictingAssociationsAreRejected() {
        val definition = """{"version":1,"mode":"replace","extensions":[".demo"],"rules":[{"words":["x"]}]}"""
        rejects { configuration("one" to definition, "two" to definition) }
    }

    @Test fun markdownRetainsFormattingAndEmbeddedCodeResetsOuterStyles() {
        val result = highlight(EditorHighlightConfiguration.Empty, "markdown", "# Title\n\n**bold** _italic_ ~~gone~~ [link](https://example.com)\n")
        val text = result.text.toString()
        assertTrue(result.styleAt(text.indexOf("Title")).bold)
        assertTrue(result.styleAt(text.indexOf("bold")).bold)
        assertTrue(result.styleAt(text.indexOf("italic")).italic)
        assertTrue(result.styleAt(text.indexOf("gone")).strike)
        val nested = EditorStyleResolver().resolve(listOf("text.html.markdown", "markup.bold", "markup.italic"))
        assertTrue(nested.bold && nested.italic)
        val embedded = EditorStyleResolver().resolve(listOf("text.html.markdown", "markup.bold", "source.js", "keyword.control.js"))
        assertFalse(embedded.bold)
        assertEquals("keyword", embedded.colorRole)
    }

    @Test fun replacingRulesInvalidatesCachedLinesButPreservesOldSnapshots() {
        val first = configuration("text" to """{"version":1,"mode":"replace","styles":{"mark":{"light":"#123456"}},"rules":[{"words":["mark"],"style":"mark"}]}""")
        val second = configuration("text" to """{"version":1,"mode":"replace","styles":{"mark":{"light":"#654321"}},"rules":[{"words":["mark"],"style":"mark"}]}""")
        val snapshot = EditorTextSnapshot(EditorLineIndex.fromText("mark"), 7, Any())
        val before = TextMateTestResources(first).use { checkNotNull(EditorTextMateHighlighter.highlight(snapshot, it.rules("text"))) }
        val after = TextMateTestResources(second).use { checkNotNull(EditorTextMateHighlighter.highlight(snapshot, it.rules("text"))) }
        assertEquals(0xFF123456.toInt(), before.styleAt(0).light)
        assertEquals(0xFF654321.toInt(), after.styleAt(0).light)
        assertEquals(1, after.computedLines)
    }

    @Test fun bothRenderersCanResolveTheSameStylesForLightAndDarkThemes() {
        val style = EditorHighlightStyle(light = 0xFF123456.toInt(), dark = 0xFF654321.toInt(), bold = true, underline = true)
        assertEquals(0xFF123456.toInt(), EditorHighlightRendering(LightTheme).resolve(style).color)
        val dark = EditorHighlightRendering(DarkTheme).resolve(style)
        assertEquals(0xFF654321.toInt(), dark.color)
        assertTrue(dark.bold && dark.underline)
    }

    @Test fun aliasesInheritConfiguredBaseColorsAndWhitespaceLiteralsRemainMatchable() {
        val config = configuration("_global" to """{
            "version":1,"styles":{"default":{"light":"#112233"},"string":{"light":"#445566"}}
        }""", "text" to """{
            "version":1,"mode":"replace","rules":[{"literals":[" "],"style":"string"}]
        }""")
        val resolver = EditorStyleResolver(config, "text")
        assertEquals(0xFF112233.toInt(), resolver.resolve(listOf("source.test", "keyword.operator.test")).light)
        assertEquals(0xFF445566.toInt(), resolver.resolve(listOf("text.html.markdown", "markup.inline.raw.markdown")).light)
        assertEquals(0xFF445566.toInt(), highlight(config, "text", "x x").styleAt(1).light)
    }

    @Test fun explicitDefaultContentClearsOuterStringFormatting() {
        val config = configuration("text" to """{
            "version":1,"mode":"replace","styles":{"string":{"italic":true,"underline":true}},
            "rules":[{"begin":"\"","end":"\"","style":"string","rules":[
                {"begin":"\\$\\{","end":"\\}","contentStyle":"default","rules":[{"words":["if"],"style":"keyword"}]}
            ]}]
        }""")
        val text = "\"text ${'$'}{if}\""
        val result = highlight(config, "text", text)
        assertTrue(result.styleAt(text.indexOf("text")).italic)
        assertTrue(result.styleAt(text.indexOf("text")).underline)
        val embedded = result.styleAt(text.indexOf("if"))
        assertEquals("keyword", embedded.colorRole)
        assertFalse(embedded.italic)
        assertFalse(embedded.underline)
    }

    @Test fun directIncludeDepthAndRepeatedExpansionAreBounded() {
        val chain = (0 until 65).joinToString(",") { index ->
            val rule = if (index == 64) """{"words":["mark"]}""" else """{"include":"rule${index + 1}"}"""
            """"rule$index":[$rule]"""
        }
        rejects { configuration("text" to """{"version":1,"mode":"replace","rules":[{"include":"rule0"}],"definitions":{$chain}}""") }
        val repeated = (0 until 16).joinToString(",") { index ->
            val rules = if (index == 15) """{"words":["mark"]}""" else
                """{"include":"rule${index + 1}"},{"include":"rule${index + 1}"}"""
            """"rule$index":[$rules]"""
        }
        rejects { configuration("text" to """{"version":1,"mode":"replace","rules":[{"include":"rule0"}],"definitions":{$repeated}}""") }
    }

    @Test fun emptyAndWhitespaceFilesRemainPendingUntilWritten() {
        val directory = temporary.newFolder()
        directory.resolve("_global.json").writeText("")
        val pending = directory.resolve("demolang.json")
        pending.writeText(" \n\t\r")
        directory.resolve("text.json").writeText("""{"version":1,"styles":{"default":{"light":"#123456"}}}""")
        val initial = EditorHighlightFiles.load(directory)
        assertNull(initial.associatedLanguage("sample.operitdemo"))
        assertEquals(0xFF123456.toInt(), initial.stylesFor("text").getValue("default").light)
        pending.writeText("""{"version":1,"mode":"replace","extensions":[".operitdemo"],"rules":[{"words":["hello"],"style":"keyword"}]}""")
        assertEquals("demolang", EditorHighlightFiles.load(directory).associatedLanguage("sample.OPERITDEMO"))
        pending.writeText("{")
        rejects { EditorHighlightFiles.load(directory) }
    }

    @Test fun invalidUtf8ReportsTheConfigurationFile() {
        val directory = temporary.newFolder()
        directory.resolve("text.json").writeBytes(byteArrayOf(0xC3.toByte(), 0x28.toByte()))
        val failure = assertThrows(IllegalArgumentException::class.java) { EditorHighlightFiles.load(directory) }
        assertTrue(checkNotNull(failure.message).contains("text.json"))
    }

    @Test fun fileLoaderReadsUtf8AndSurfacesInvalidRevisions() {
        val directory = temporary.newFolder()
        val file = directory.resolve("text.json")
        file.writeText("""{"version":1,"styles":{"中文":{"light":"#123456"}},"scopes":[{"scope":"text","style":"中文"}]}""", Charsets.UTF_8)
        assertEquals(0xFF123456.toInt(), EditorStyleResolver(EditorHighlightFiles.load(directory), "text").resolve(listOf("text.operit.plain")).light)
        file.writeText("""{"version":2}""", Charsets.UTF_8)
        rejects { EditorHighlightFiles.load(directory) }
    }
}
