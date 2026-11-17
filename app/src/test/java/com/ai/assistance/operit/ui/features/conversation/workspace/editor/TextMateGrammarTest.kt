package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateCatalog
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateGrammarRegistry
import java.io.IOException
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TextMateGrammarTest {
    private lateinit var resources: TextMateTestResources
    @Before fun prepare() { resources = TextMateTestResources() }
    @After fun release() { resources.close() }

    @Test fun allLanguageDefinitionsLoadTheirActualBundledResources() {
        for (language in TextMateCatalog.languages) {
            assertNotNull(language.id, resources.registry.rulesFor(language.id))
        }
    }

    @Test fun commonLanguagesColorStringsAndNumbersAfterChineseAndEmoji() {
        val samples = mapOf(
            "kotlin" to "val s = \"中文👋\"; val n = 42",
            "java" to "String s = \"中文👋\"; int n = 42;",
            "javascript" to "const s = \"中文👋\"; const n = 42;",
            "typescript" to "const s: string = \"中文👋\"; const n = 42;",
            "python" to "s = \"中文👋\"; n = 42",
            "c" to "char *s = \"中文👋\"; int n = 42;",
            "cpp" to "auto s = \"中文👋\"; int n = 42;",
            "csharp" to "string s = \"中文👋\"; int n = 42;",
            "go" to "s := \"中文👋\"; n := 42",
            "rust" to "let s = \"中文👋\"; let n = 42;",
            "dart" to "var s = \"中文👋\"; var n = 42;",
            "swift" to "let s = \"中文👋\"; let n = 42",
            "json" to "{\"s\": \"中文👋\", \"n\": 42}",
            "toml" to "s = \"中文👋\"\nn = 42",
            "yaml" to "s: \"中文👋\"\nn: 42",
            "lua" to "local s = \"中文👋\"; local n = 42",
        )
        for ((language, text) in samples) {
            val result = highlight(language, text)
            val string = text.indexOf("中文")
            for (offset in string until string + "中文👋".length) assertEquals(language, EditorTokenKind.STRING, result.kindAt(offset))
            assertEquals(language, EditorTokenKind.NUMBER, result.kindAt(text.lastIndexOf("42")))
        }
    }

    @Test fun actionScriptUsesItsActualGrammarForDeclarationsStringsCommentsAndNumbers() {
        val text = """
            package demo {
                import flash.display.Sprite;
                public class Example extends Sprite {
                    [Bindable]
                    private var greeting:String = "中文👋";
                    public function Example() {
                        /* 多行注释
                           注释结束 */
                        var count:Number = 42;
                        trace(greeting); // 行注释
                    }
                }
            }
        """.trimIndent()
        val result = highlight("as", text)
        for (keyword in listOf("package", "import", "class", "function")) {
            assertEquals(keyword, EditorTokenKind.KEYWORD, result.kindAt(text.indexOf(keyword)))
        }
        assertEquals(EditorTokenKind.TYPE, result.kindAt(text.indexOf("class Example") + "class ".length))
        assertEquals(EditorTokenKind.TYPE, result.kindAt(text.indexOf("Number")))
        assertEquals(EditorTokenKind.STRING, result.kindAt(text.indexOf("中文")))
        assertEquals(EditorTokenKind.COMMENT, result.kindAt(text.indexOf("注释结束")))
        assertEquals(EditorTokenKind.COMMENT, result.kindAt(text.lastIndexOf("行注释")))
        assertEquals(EditorTokenKind.NUMBER, result.kindAt(text.indexOf("42")))
        assertEquals(EditorTokenKind.FUNCTION, result.kindAt(text.indexOf("trace")))
    }

    @Test fun htmlAndMarkdownCarryEmbeddedJavascriptAcrossLineBoundaries() {
        val html = "<script>\nconst x = \"hello\";\n</script>\n<style>\n.a { color: red; }\n</style>"
        val htmlResult = highlight("html", html)
        assertEquals(EditorTokenKind.KEYWORD, htmlResult.kindAt(html.indexOf("const")))
        assertEquals(EditorTokenKind.STRING, htmlResult.kindAt(html.indexOf("hello")))
        val markdown = "# Title\n```javascript\nconst x = \"hello\";\n```\nplain text"
        val markdownResult = highlight("markdown", markdown)
        assertEquals(EditorTokenKind.KEYWORD, markdownResult.kindAt(markdown.indexOf("const")))
        assertEquals(EditorTokenKind.STRING, markdownResult.kindAt(markdown.indexOf("hello")))
    }

    @Test fun jsxAndTsxDistinguishComponentTypesFromHtmlTags() {
        for (language in listOf("jsx", "tsx")) {
            val text = "const node = <Button title=\"hello\"><span>{42}</span></Button>;"
            val result = highlight(language, text)
            // 大写组件使用类型作用域，普通 HTML 标签保留标签作用域。
            assertEquals(language, EditorTokenKind.TYPE, result.kindAt(text.indexOf("Button")))
            assertEquals(language, EditorTokenKind.KEYWORD, result.kindAt(text.indexOf("span")))
            assertEquals(language, EditorTokenKind.STRING, result.kindAt(text.indexOf("hello")))
            assertEquals(language, EditorTokenKind.NUMBER, result.kindAt(text.indexOf("42")))
        }
    }

    @Test fun xmlAspCommentsCloseBeforeTheFollowingTag() {
        val text = "<%-- 中文👋 --%>\n<node value=\"hello\" />"
        val result = highlight("xml", text)
        assertEquals(EditorTokenKind.COMMENT, result.kindAt(text.indexOf("中文")))
        assertEquals(EditorTokenKind.KEYWORD, result.kindAt(text.indexOf("node")))
        assertEquals(EditorTokenKind.STRING, result.kindAt(text.indexOf("hello")))
    }

    @Test fun racketRepositoryPatternsAndNamedCapturesUseRuleObjects() {
        val text = "(lambda x \"中文👋\")"
        val result = highlight("racket", text)
        assertEquals(EditorTokenKind.KEYWORD, result.kindAt(text.indexOf("lambda")))
        assertEquals(EditorTokenKind.STRING, result.kindAt(text.indexOf("中文")))
    }

    @Test fun stataStringCaptureNamesRetainTheirKeywordScope() {
        val text = "format value %9.2f"
        val result = highlight("stata", text)
        assertEquals(EditorTokenKind.KEYWORD, result.kindAt(text.indexOf("format")))
    }

    @Test fun wikitextEmptyCapturesPreserveRedirectGroupNumbers() {
        val text = "#REDIRECT [[Target]]"
        val result = highlight("wikitext", text)
        assertEquals(EditorTokenKind.KEYWORD, result.kindAt(0))
        assertEquals(EditorTokenKind.VARIABLE, result.kindAt(text.indexOf("Target")))
    }

    @Test fun jinjaCaptureArraysRetainTheirZeroGroupAndEmbeddedStrings() {
        val text = "{{ \"中文👋\" }}"
        val result = highlight("jinja", text)
        assertEquals(EditorTokenKind.VARIABLE, result.kindAt(0))
        assertEquals(EditorTokenKind.STRING, result.kindAt(text.indexOf("中文")))
        assertEquals(EditorTokenKind.VARIABLE, result.kindAt(text.indexOf("}}")))
    }

    @Test fun capturesAndDynamicEndRulesKeepUtf16OffsetsAfterEmoji() {
        val text = "const s = \"👋\"; const regex = /(?<word>a+)[0-9]/; // 中文"
        val result = highlight("javascript", text)
        assertEquals(EditorTokenKind.KEYWORD, result.kindAt(text.indexOf("const regex")))
        assertEquals(EditorTokenKind.COMMENT, result.kindAt(text.indexOf("//")))
        val ruby = "value = <<'END'\n中文👋\nEND\nnumber = 42"
        val rubyResult = highlight("ruby", ruby)
        assertEquals(EditorTokenKind.STRING, rubyResult.kindAt(ruby.indexOf("中文")))
        assertEquals(EditorTokenKind.NUMBER, rubyResult.kindAt(ruby.lastIndexOf("42")))
    }

    @Test fun emptyAndTrailingLinesAreRepresentedWithoutOutOfRangeSpans() {
        for (text in listOf("", "\n", "val s = \"👋\"\n")) {
            val result = highlight("kotlin", text)
            assertEquals(EditorTokenKind.DEFAULT, result.kindAt(-1))
            assertEquals(EditorTokenKind.DEFAULT, result.kindAt(text.length))
            if (text.endsWith('\n')) assertEquals(EditorTokenKind.DEFAULT, result.kindAt(text.length - 1))
        }
    }

    @Test fun loadingFailuresKeepTheOriginalCauseAndDoNotRunAnotherParser() {
        val failure = IOException("missing test asset")
        var opens = 0
        val registry = TextMateGrammarRegistry { opens++; throw failure }
        val first = assertThrows(RuntimeException::class.java) { registry.rulesFor("kotlin") }
        val second = assertThrows(RuntimeException::class.java) { registry.rulesFor("kotlin") }
        assertSame(first, second)
        assertSame(failure, first.cause)
        assertEquals(1, opens)
        assertNotNull(registry.rulesFor("text"))
        assertEquals(1, opens)
    }

    @Test fun additionalLanguageRulesProduceOrderedUtf16TokenBoundaries() {
        val languages = listOf("shellscript", "powershell", "sql", "css", "scss", "vue", "svelte", "docker", "make", "zig", "r", "haskell", "elixir", "xml", "json5", "jsonc", "hcl", "terraform", "nginx", "fish")
        for (language in languages) {
            val text = "name = \"中文👋\" 42"
            val result = resources.rules(language).grammar.tokenizeLine(text, null, Duration.ofSeconds(1))
            assertFalse(language, result.isStoppedEarly)
            var end = 0
            for (token in result.tokens) {
                assertTrue(language, token.startIndex >= end)
                assertTrue(language, token.endIndex >= token.startIndex)
                assertTrue(language, token.endIndex <= text.length + 1)
                end = token.endIndex
            }
        }
    }

    private fun highlight(language: String, text: String): HighlightSnapshot = checkNotNull(
        EditorTextMateHighlighter.highlight(EditorTextSnapshot(EditorLineIndex.fromText(text), 0, Any()), resources.rules(language))
    )
}
