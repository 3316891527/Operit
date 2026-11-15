package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateCatalog
import org.junit.Assert.*
import org.junit.Test
import com.google.gson.JsonParser

class TextMateCatalogTest {
    @Test fun catalogSeparatesLanguagesFromInjectionGrammars() {
        assertEquals(242, TextMateCatalog.languages.size)
        assertEquals(260, TextMateCatalog.grammars.size)
        assertEquals(260, TextMateCatalog.grammars.map { it.scopeName }.toSet().size)
    }

    @Test fun languageVariantsAndCompoundSuffixesHaveDistinctGrammars() {
        val expected = mapOf("index.js" to "javascript", "index.jsx" to "jsx", "index.ts" to "typescript",
            "index.tsx" to "tsx", "view.scss" to "scss", "view.sass" to "sass", "view.less" to "less",
            "view.vue" to "vue", "build.gradle.kts" to "kotlin", "view.blade.php" to "blade",
            "schema.tf.json" to "terraform", "test.c" to "c", "test.cpp" to "cpp")
        for ((name, language) in expected) {
            assertEquals(name, language, LanguageDetector.detectLanguage(name))
            assertNotNull(TextMateCatalog.language(language))
        }
    }

    @Test fun actionScriptExtensionAndAliasesUseTheExistingLanguageAndCommentRules() {
        for (path in listOf("Main.as", "/src/Main.AS", "C:\\src\\Main.as")) {
            assertEquals("actionscript-3", LanguageDetector.detectLanguage(path))
        }
        for (alias in listOf("as", "as3", "actionscript", "actionscript-3")) {
            assertEquals(alias, "actionscript-3", TextMateCatalog.language(alias)!!.id)
            val comments = checkNotNull(CommentSyntaxRegistry.forLanguage(alias))
            assertEquals("//", comments.preferredLinePrefix)
            assertEquals("/*", comments.blockStart)
            assertEquals("*/", comments.blockEnd)
        }
    }

    @Test fun standaloneLanguagesHaveFileAssociationsAndSharedVariantsRemainEmbedded() {
        val catalog = checkNotNull(javaClass.getResourceAsStream("/catalog.json")).bufferedReader(Charsets.UTF_8).use {
            JsonParser.parseReader(it).asJsonObject
        }
        val associated = listOf("extensions", "fileNames", "fileNamePrefixes").flatMap { field ->
            catalog.getAsJsonObject(field).entrySet().map { TextMateCatalog.canonicalId(it.value.asString) }
        }.toSet()
        // 这些规则服务于嵌入代码或已有同格式语法，不为其编造独立后缀。
        val sharedVariants = setOf("angular-html", "angular-ts", "html-derivative", "nextflow-groovy", "tex", "ts-tags", "vue-html")
        assertEquals(sharedVariants, TextMateCatalog.languages.map { it.id }.toSet() - associated)
        val expected = mapOf("app.ahk2" to "ahk2", "query.dax" to "dax", "api.hurl" to "hurl",
            "drawing.logo" to "logo", "drawing.lgo" to "logo", "page.mdc" to "mdc", "story.nar" to "narrat",
            "qmldir" to "qmldir", "theme.qss" to "qss", "query.rel" to "rel", "worker.socket" to "systemd",
            "worker.service.in" to "systemd", "worker.timer" to "systemd", "示例.wy" to "wenyan")
        for ((name, language) in expected) assertEquals(name, language, LanguageDetector.detectLanguage(name))
        assertEquals("desktop", LanguageDetector.detectLanguage("dbus.service"))
        assertEquals("ahk", LanguageDetector.detectLanguage("legacy.ahk"))
        assertEquals("markdown", LanguageDetector.detectLanguage("README.md"))
    }

    @Test fun specialFileNamesAndConfigurationPrefixesDoNotNeedAnExtension() {
        val expected = mapOf("Dockerfile" to "docker", "Dockerfile.dev" to "docker", "Makefile" to "make",
            "CMakeLists.txt" to "cmake", ".bashrc" to "shellscript", ".env.local" to "dotenv",
            "Gemfile" to "ruby", "tsconfig.json" to "jsonc", "CODEOWNERS" to "codeowners")
        for ((name, language) in expected) assertEquals(name, language, LanguageDetector.detectLanguage(name))
    }

    @Test fun filePathsAndCaseUseTheSameDirectoryAndUnknownFilesArePlainText() {
        assertEquals("kotlin", LanguageDetector.detectLanguage("/project/APP.KT"))
        assertEquals("python", LanguageDetector.detectLanguage("C:\\project\\APP.PY"))
        for (name in listOf("file.bin", "notes.txt", "unknown.conf", "include.inc", "README")) {
            assertEquals(name, "text", LanguageDetector.detectLanguage(name))
        }
        assertNull(TextMateCatalog.language("text"))
        assertNull(TextMateCatalog.language("plaintext"))
        assertThrows(IllegalArgumentException::class.java) { TextMateCatalog.language("missing-language") }
    }

    @Test fun legacyNamesAndNewGrammarIdsKeepCommentActionsReachable() {
        for (name in listOf("shell", "shellscript", "jsx", "tsx", "c", "csharp", "coffee", "bat", "viml", "scss", "less")) {
            assertNotNull(name, TextMateCatalog.language(name))
            assertNotNull(name, CommentSyntaxRegistry.forLanguage(name))
        }
        assertEquals("ini", TextMateCatalog.language("properties")!!.id)
        assertEquals("properties", LanguageDetector.detectLanguage("app.properties"))
    }

    @Test fun scopeAliasesAndInjectionTargetsResolveToTheBundledDefinitions() {
        assertEquals("cpp", TextMateCatalog.grammarForScope("source.c++")!!.id)
        assertEquals("vue", TextMateCatalog.grammarForScope("source.vue")!!.id)
        assertTrue(TextMateCatalog.injectionsFor("source.vue").isNotEmpty())
        val vue = TextMateCatalog.language("vue")!!
        assertEquals(TextMateCatalog.injectionsFor("source.vue"), TextMateCatalog.injectionsFor(vue.scopeName))
        assertNull(TextMateCatalog.grammarForScope("source.arm"))
        assertThrows(IllegalStateException::class.java) { TextMateCatalog.grammarForScope("source.missing-new-resource") }
    }
}
