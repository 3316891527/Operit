package com.ai.assistance.operit.util.markdown

import org.junit.Assert.*
import org.junit.Test

class MarkdownSyntaxTest {
    @Test fun parsesTaskStateAndNestedMarkers() {
        val pending = markdownListMarker("  - [ ] pending")!!
        assertEquals(2, pending.indent)
        assertEquals(false, pending.checked)
        assertEquals("pending", "  - [ ] pending".drop(pending.prefixLength))
        assertEquals(true, markdownListMarker("12. [X] done")!!.checked)
        assertEquals("12", markdownListMarker("12) value")!!.number)
        assertNull(markdownListMarker("not a list"))
    }

    @Test fun protectsPipesInCodeAndEscapesWithoutLosingEmptyCells() {
        assertEquals(listOf("a|b", "`x|y`", "", "end"), markdownTableCells("| a\\|b | `x|y` | | end |"))
        assertEquals(listOf("a", ""), markdownTableCells("| a | |"))
        assertEquals(listOf("a\\\\", "b"), markdownTableCells("| a\\\\| b |"))
    }

    @Test fun parsesAllColumnAlignments() {
        assertEquals(listOf(MarkdownColumnAlignment.LEFT, MarkdownColumnAlignment.CENTER, MarkdownColumnAlignment.RIGHT),
            markdownTableAlignments("| :--- | :---: | ---: |"))
        assertNull(markdownTableAlignments("| text | --- |"))
    }

    @Test fun parsesDestinationTitlesAndBalancedParentheses() {
        assertEquals(MarkdownDestination("https://example.com/a_(b)", "标题"), MarkdownDestination.parse("https://example.com/a_(b) \"标题\""))
        assertEquals(MarkdownDestination("images/a b.png", "image"), MarkdownDestination.parse("<images/a b.png> 'image'"))
        assertEquals(MarkdownDestination("a.png"), MarkdownDestination.parse("a.png"))
        assertNull(MarkdownDestination.parse("<broken"))
    }

    @Test fun resolvesRelativeImagesAgainstDocumentDirectory() {
        assertEquals("file:///sdcard/docs/images/a%20b.png", resolveMarkdownResource("images/a b.png", "/sdcard/docs/readme.md"))
        assertEquals("file:///sdcard/a.png", resolveMarkdownResource("../a.png", "/sdcard/docs/readme.md"))
        assertEquals("file:///sdcard/docs/a%20b.png?x=1#top", resolveMarkdownResource("a%20b.png?x=1#top", "/sdcard/docs/readme.md"))
        assertEquals("https://example.com/a.png", resolveMarkdownResource("https://example.com/a.png", "/sdcard/docs/readme.md"))
    }

    @Test fun resolvesForwardCollapsedAndShortcutReferences() {
        val document = MarkdownDocumentSyntax.prepare("[Go][Ref]\n[Ref][]\n[ref]\n\n[ref]: https://example.com \"title\"")
        assertEquals(3, Regex("https://example.com").findAll(document.body).count())
        assertFalse(document.body.contains("[ref]:"))
        assertTrue(document.body.contains("[Go](<https://example.com> \"title\")"))
    }

    @Test fun doesNotResolveReferencesInsideInlineCodeOrEscapedBrackets() {
        val document = MarkdownDocumentSyntax.prepare("`[Go][ref]` \\[ref] [Go][ref]\n\n[ref]: https://example.com")
        assertTrue(document.body.startsWith("`[Go][ref]` \\[ref] [Go]("))
    }

    @Test fun preservesFencesMathAndToolXml() {
        val source = "```md\n# hidden\n[ref]: fake\n<div>example</div>\n```\n\n$$\n[ref]: math\n$$\n\n<tool>\n<div>content</div>\n[ref]: tool\n</tool>\n\n[Go][ref]\n[ref]: https://example.com"
        val document = MarkdownDocumentSyntax.prepare(source)
        assertTrue(document.body.contains("[ref]: fake"))
        assertTrue(document.body.contains("[ref]: math"))
        assertTrue(document.body.contains("[ref]: tool"))
        assertTrue(document.body.contains("[Go](<https://example.com>)"))
        assertTrue(MarkdownDocumentSyntax.blocks(document.body).none { it.kind == MarkdownSourceKind.HTML })
    }

    @Test fun convertsSetextWithoutConvertingFencedExamples() {
        val document = MarkdownDocumentSyntax.prepare("Title\n===\n\nSubtitle\n---\n\n```text\nHidden\n===\n```")
        assertTrue(document.body.startsWith("# Title\n"))
        assertTrue(document.body.contains("## Subtitle\n"))
        assertTrue(document.body.contains("Hidden\n===\n"))
    }

    @Test fun expandsTocWithDuplicateAndUnicodeAnchors() {
        val document = MarkdownDocumentSyntax.prepare("[TOC]\n\n# 标题\n## Repeated\n## Repeated\n```text\n# hidden\n```")
        assertTrue(document.body.contains("- [标题](#标题)"))
        assertTrue(document.body.contains("- [Repeated](#repeated-1)"))
        assertFalse(document.body.contains("[hidden]"))
    }

    @Test fun numbersFootnotesInReferenceOrderAndKeepsRepeatedNumbers() {
        val document = MarkdownDocumentSyntax.prepare("B[^b] A[^a] B[^b]\n\n[^a]: first\n[^b]: second")
        assertEquals(2, document.footnotes.size)
        assertTrue(document.body.startsWith("B^[1](#fn-62)^ A^[2](#fn-61)^ B^[1](#fn-62)^"))
        assertEquals("62\n1\nsecond", document.footnotes[0].content)
        assertEquals("61\n2\nfirst", document.footnotes[1].content)
    }

    @Test fun collectsMultilineAndCrossReferencedFootnotesOnce() {
        val document = MarkdownDocumentSyntax.prepare("text[^a]\n\n[^a]: line one\n    line two [^b]\n[^b]: see [^a]")
        assertEquals(2, document.footnotes.size)
        assertTrue(document.footnotes.first().content.contains("line one\nline two ^[2]"))
    }

    @Test fun preservesNestedQuoteAndNestedListSourceForRecursiveRendering() {
        val blocks = MarkdownDocumentSyntax.blocks("> **bold**\n> > inner\n\n- [x] outer\n  - inner\n\n- next\n")
        assertEquals(listOf(MarkdownSourceKind.QUOTE, MarkdownSourceKind.UNORDERED_ITEM, MarkdownSourceKind.UNORDERED_ITEM), blocks.map { it.kind })
        assertTrue(blocks[0].content.contains("> > inner"))
        assertTrue(blocks[1].content.contains("  - inner"))
    }

    @Test fun keepsNestedHtmlInOneBlockAndPreservesFollowingParagraph() {
        val blocks = MarkdownDocumentSyntax.blocks("<div>\n<div>inner</div>\n</div>\n\nAfter")
        assertEquals(listOf(MarkdownSourceKind.HTML, MarkdownSourceKind.NATIVE), blocks.map { it.kind })
        assertTrue(blocks[0].content.endsWith("</div>"))
        assertEquals(3, blocks[0].content.lines().size)
        assertTrue(blocks[1].content.contains("After"))
    }

    @Test fun recognizesDefinitionsAndInlineHtmlStyles() {
        val document = MarkdownDocumentSyntax.prepare("Word\n: **definition**\n\nH<sub>2</sub>O and x<sup>2</sup> and <mark>mark</mark>")
        val blocks = MarkdownDocumentSyntax.blocks(document.body)
        assertEquals(MarkdownSourceKind.DEFINITION, blocks[0].kind)
        assertTrue(document.body.contains("H~2~O and x^2^ and ==mark=="))
    }

    @Test fun resolvesImageReferencesWithoutLeakingTheDefinition() {
        val document = MarkdownDocumentSyntax.prepare("![image][asset]\n\n[asset]: picture.png \"title\"")
        assertTrue(document.body.contains("![image](<picture.png> \"title\")"))
        assertFalse(document.body.contains("[asset]:"))
    }

    @Test fun leavesUnknownDefinitionsAndReferencesAsText() {
        val document = MarkdownDocumentSyntax.prepare("[unknown] [unknown][missing]\n\n[bad]: <broken")
        assertTrue(document.body.contains("[unknown] [unknown][missing]"))
        assertTrue(document.body.contains("[bad]: <broken"))
    }
}