package com.ai.assistance.operit.core.tools.system.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalOutputCompressorTest {
    @Test fun gradleBuildRetainsFirstLastAndCompletion() {
        val tasks = gradleLines(80)
        val output = tasks + "BUILD SUCCESSFUL in 4s\n80 actionable tasks: 80 executed\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("./gradlew :app:assembleDebug --console=plain", output))
        assertEquals(78, summary.omittedLines)
        assertEquals("gradle-progress", summary.strategy)
        assertEquals(
            tasks.lineSequence().first() + "\n[已省略 78 行已识别的进度或成功日志]\n" +
                tasks.trimEnd('\n').substringAfterLast('\n') + "\n" +
                "BUILD SUCCESSFUL in 4s\n80 actionable tasks: 80 executed\n",
            summary.output,
        )
        assertSavings(output, summary)
    }

    @Test fun gradleFailurePreservesCompleteDiagnosticSuffix() {
        val failure = "> Task :app:compileDebugKotlin FAILED\n" +
            "e: /项目/Main.kt:8: 类型不匹配\n" +
            "warning: deprecated API\nwarning: deprecated API\n" +
            "java.lang.IllegalStateException: broken\n\tat demo.Main.run(Main.kt:8)\n" +
            "Caused by: java.io.IOException: disk\n\t... 4 more\n" +
            "FAILURE: Build failed with an exception.\nBUILD FAILED in 4s\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle build", gradleLines(80) + failure))
        assertTrue(summary.output.endsWith(failure))
        assertEquals(78, summary.omittedLines)
    }

    @Test fun unknownLinesAndBlankLinesKeepTheirOrder() {
        val separator = "\n任意业务数据：甲乙丙\n  keep spaces  \n\n"
        val tail = "原始结果：完成\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle test", gradleLines(40) + separator + gradleLines(40) + tail))
        assertEquals(76, summary.omittedLines)
        assertTrue(summary.output.contains(separator))
        assertTrue(summary.output.endsWith(tail))
        assertEquals(2, summary.output.lineSequence().count { it.startsWith("[已省略 ") })
    }

    @Test fun repeatedUnknownDataIsNeverDeduplicated() {
        val output = "customer-record-1234567890\n".repeat(200)
        assertNull(TerminalOutputCompressor.compress("gradle build", output))
    }

    @Test fun shortOutputRemainsUnchanged() {
        assertNull(TerminalOutputCompressor.compress("gradle build", gradleLines(10)))
        assertNull(TerminalOutputCompressor.compress("cargo test", "test demo ... ok\n"))
        assertNull(TerminalOutputCompressor.compress("pytest", ""))
    }

    @Test fun minimumInputLengthBoundaryIsRespected() {
        val progress = gradleLines(40)
        val below = progress + "x".repeat(1999 - progress.length)
        assertEquals(1999, below.length)
        assertNull(TerminalOutputCompressor.compress("gradle build", below))
        val eligible = below + "x"
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle build", eligible))
        assertEquals(2000, eligible.length)
        assertSavings(eligible, summary)
    }

    @Test fun longOutputWithoutKnownProgressRemainsUnchanged() {
        val output = "unknown result must survive verbatim\n".repeat(100)
        assertNull(TerminalOutputCompressor.compress("gradle build", output))
    }

    @Test fun savingsBelowTwentyPercentDoesNotProduceSummary() {
        val output = gradleLines(60) + "unrecognized business payload 0123456789\n".repeat(800)
        assertNull(TerminalOutputCompressor.compress("gradle build", output))
    }

    @Test fun unknownAndDataReadingCommandsAreExcluded() {
        val commands = listOf(
            "cat build.log", "read_file build.log", "grep Task build.log", "rg Task build.log",
            "git diff", "git status", "echo build", "custom-builder test", "gradle properties",
            "cargo metadata", "go version", "npm run export", "pnpm list", "mvn help:evaluate",
        )
        commands.forEach { assertNull(it, TerminalOutputCompressor.compress(it, gradleLines(80))) }
    }

    @Test fun machineReadableOptionsAreExcludedForRecognizedCommands() {
        val cases = listOf(
            "gradle build" to "--json", "gradle build" to "--format=json", "gradle build" to "--porcelain",
            "gradle build" to "-z", "gradle build" to "--xml", "gradle build" to "--csv",
            "cargo test" to "--message-format=json", "cargo test" to "-- --format json",
            "go test" to "-json ./...", "pytest" to "--junitxml=report.xml", "pytest" to "--collect-only",
            "npm test" to "-- --reporter=json", "pnpm test" to "--reporters json",
            "mvn test" to "--output result.txt", "gradle build" to "--null", "npm test" to "-- --tap",
        )
        cases.forEach { (command, option) ->
            val output = when (command.substringBefore(' ')) {
                "gradle" -> gradleLines(80)
                "cargo" -> (1..100).joinToString("") { "test module::test_case_$it ... ok\n" }
                "go" -> (1..100).joinToString("") { "ok\texample.org/package$it\t0.012s\n" }
                "pytest" -> (1..100).joinToString("") { "tests/test_sample.py::test_case_$it PASSED [ 50%]\n" }
                "npm", "pnpm" -> (1..100).joinToString("") { "ok $it - successful behavior $it\n" }
                "mvn" -> (1..100).joinToString("") {
                    "[INFO] Downloading from central: https://repo.example.org/library-$it.jar\n"
                }
                else -> throw IllegalArgumentException(command)
            }
            requireNotNull(TerminalOutputCompressor.compress(command, output))
            assertNull("$command $option", TerminalOutputCompressor.compress("$command $option", output))
        }
    }

    @Test fun shellCompositionAndQuotingAreExcluded() {
        val commands = listOf(
            "gradle build | cat", "gradle build > output.log", "gradle build 2>&1",
            "gradle build && echo done", "gradle build; echo done", "gradle build || true",
            "gradle build\ncat data", "gradle build &", "(gradle build)",
            "gradle build `date`", "gradle build ${'$'}(date)", "gradle build '${'$'}x'",
            "gradle build \"quoted\"", "gradle build # comment", "gradle build *.kt",
            "gradle build \\\n test",
        )
        commands.forEach { assertNull(it, TerminalOutputCompressor.compress(it, gradleLines(80))) }
    }

    @Test fun structuredPayloadsAndPatchesAreExcludedEvenForBuildCommand() {
        val progress = gradleLines(80)
        val outputs = listOf(
            "{\n\"data\": [\n" + progress + "]}\n",
            "[\n" + progress + "]\n",
            "<?xml version=\"1.0\"?>\n<log>\n" + progress + "</log>\n",
            "name,status\n" + progress,
            "diff --git a/a b/a\n--- a/a\n+++ b/a\n@@ -1 +1 @@\n" + progress,
            "--- old.txt\n+++ new.txt\n" + progress,
            progress + "<payload>structured data</payload>\n",
        )
        outputs.forEach { assertNull(TerminalOutputCompressor.compress("gradle build", it)) }
    }

    @Test fun cargoBuildAndPassedTestsCanBeFolded() {
        val build = (1..100).joinToString("") { "   Compiling dependency_$it v1.2.3\n" }
        val tests = (1..100).joinToString("") { "test module::test_case_$it ... ok\n" }
        val tail = "test result: ok. 100 passed; 0 failed; 0 ignored\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("cargo test", build + tests + tail))
        assertEquals("cargo-progress", summary.strategy)
        assertEquals(198, summary.omittedLines)
        assertTrue(summary.output.endsWith(tail))
        assertSavings(build + tests + tail, summary)
    }

    @Test fun cargoFailuresAndStackTracesSurvive() {
        val progress = (1..100).joinToString("") { "test module::test_case_$it ... ok\n" }
        val failure = "test module::broken ... FAILED\n\nfailures:\n" +
            "thread 'main' panicked at src/lib.rs:4: assertion failed\n" +
            "stack backtrace:\n   0: rust_begin_unwind\n   1: demo::broken\n" +
            "test result: FAILED. 100 passed; 1 failed\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("cargo test", progress + failure))
        assertTrue(summary.output.endsWith(failure))
    }

    @Test fun mavenOnlyFoldsDownloadMessages() {
        val downloads = (1..60).joinToString("") {
            "[INFO] Downloaded from central: https://repo.example.org/org/demo/lib-$it.jar (12 kB at 24 kB/s)\n"
        }
        val tail = "[WARNING] unknown plugin setting\n[WARNING] unknown plugin setting\n" +
            "[INFO] Tests run: 6, Failures: 1, Errors: 0, Skipped: 0\n" +
            "[ERROR] assertion detail\n[INFO] BUILD FAILURE\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("./mvnw test", downloads + tail))
        assertEquals("maven-downloads", summary.strategy)
        assertEquals(58, summary.omittedLines)
        assertTrue(summary.output.endsWith(tail))
    }

    @Test fun npmAndPnpmFoldTapSuccessButKeepFailedAssertions() {
        val passes = (1..100).joinToString("") { "ok $it - preserves expected behavior in case $it\n" }
        val tail = "not ok 101 - broken behavior\n  ---\n  actual: 1\n  expected: 2\n  operator: equal\n  ...\n" +
            "# tests 101\n# pass 100\n# fail 1\n"
        listOf("npm test", "npm run test", "pnpm test", "pnpm run test").forEach { command ->
            val summary = requireNotNull(TerminalOutputCompressor.compress(command, passes + tail))
            assertEquals("node-test-passes", summary.strategy)
            assertEquals(98, summary.omittedLines)
            assertTrue(summary.output.endsWith(tail))
        }
    }

    @Test fun jestPassLinesCanBeFoldedWithoutLosingFailureDetails() {
        val passes = (1..100).joinToString("") { "PASS src/features/component$it.test.ts (0.12 s)\n" }
        val tail = "FAIL src/features/broken.test.ts\n  expected 2, received 1\n  at run (broken.test.ts:3)\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("pnpm run test", passes + tail))
        assertEquals(98, summary.omittedLines)
        assertTrue(summary.output.endsWith(tail))
    }

    @Test fun pytestPassesFoldButFailureSectionIsVerbatim() {
        val passes = (1..100).joinToString("") { "tests/test_sample.py::test_case_$it PASSED [ 50%]\n" }
        val failure = "tests/test_sample.py::test_broken FAILED [100%]\n" +
            "=================================== FAILURES ===================================\n" +
            "_______________________________ test_broken _______________________________\n" +
            "    assert actual == expected\nE   AssertionError: 中文断言失败\n" +
            "======================== 1 failed, 100 passed in 1.2s ========================\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("pytest -v", passes + failure))
        assertEquals("pytest-passes", summary.strategy)
        assertEquals(98, summary.omittedLines)
        assertTrue(summary.output.endsWith(failure))
    }

    @Test fun pytestDotProgressIsRecognized() {
        val output = (1..100).joinToString("") { "tests/test_module_$it.py .......... [ 50%]\n" }
        val summary = requireNotNull(TerminalOutputCompressor.compress("pytest", output))
        assertEquals(98, summary.omittedLines)
        assertSavings(output, summary)
    }

    @Test fun goPackagePassesFoldButFailureAndPanicRemain() {
        val packages = (1..100).joinToString("") { "ok\texample.org/project/package$it\t0.012s\n" }
        val tail = "--- FAIL: TestBroken (0.00s)\n    sample_test.go:8: got 1, want 2\n" +
            "panic: broken\ngoroutine 1 [running]:\nmain.run()\n\t/project/main.go:8\n" +
            "FAIL\texample.org/project/broken\t0.013s\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("go test ./...", packages + tail))
        assertEquals("go-test-passes", summary.strategy)
        assertEquals(98, summary.omittedLines)
        assertTrue(summary.output.endsWith(tail))
    }

    @Test fun goVerbosePassesAreRecognized() {
        val output = (1..100).joinToString("") { "--- PASS: TestBehavior$it (0.00s)\n" } + "PASS\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("go test -v", output))
        assertEquals(98, summary.omittedLines)
        assertTrue(summary.output.endsWith("PASS\n"))
    }

    @Test fun diagnosticsInsideRecognizedSuccessShapesAreNeverOmitted() {
        val passes = (1..40).joinToString("") { "ok $it - normal successful behavior $it\n" }
        val diagnostic = "ok 999 - warning: suspicious result\n".repeat(8)
        val summary = requireNotNull(TerminalOutputCompressor.compress("npm test", passes + diagnostic + passes))
        assertEquals(38, summary.omittedLines)
        assertTrue(summary.output.endsWith(diagnostic + passes))
    }

    @Test fun nonAsciiUnknownAndDiagnosticLinesKeepExactCharacters() {
        val text = "构建消息：你好，世界 🌏\n警告：资源不存在\n\t断言：期望「甲」实际「乙」\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle build", gradleLines(50) + text + gradleLines(50)))
        assertTrue(summary.output.contains(text))
        assertEquals(48, summary.omittedLines)
    }

    @Test fun sgrColorsOnRetainedLinesArePreserved() {
        val output = (1..80).joinToString("") { "\u001B[32m> Task :module$it:compileDebugKotlin UP-TO-DATE\u001B[0m\n" }
        val diagnostic = "\u001B[31merror: broken\u001B[0m\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle build", output + diagnostic))
        assertEquals(78, summary.omittedLines)
        assertTrue(summary.output.startsWith(output.substringBefore('\n') + "\n"))
        assertTrue(summary.output.endsWith(diagnostic))
    }

    @Test fun unknownControlsAndTuiSequencesDisableCompression() {
        val controls = listOf("\u001B[2K", "\u001B]0;title\u0007", "\r", "\b", "\u009b", "\u0000")
        controls.forEach { control ->
            assertNull(TerminalOutputCompressor.compress("gradle build", gradleLines(80) + control))
        }
    }

    @Test fun crlfAndMissingFinalNewlineArePreserved() {
        val output = gradleLines(80).replace("\n", "\r\n") + "BUILD SUCCESSFUL"
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle build", output))
        assertEquals(78, summary.omittedLines)
        assertTrue(summary.output.contains("[已省略 78 行已识别的进度或成功日志]\r\n"))
        assertFalse(summary.output.replace("\r\n", "").contains('\n'))
        assertTrue(summary.output.endsWith("BUILD SUCCESSFUL"))
        assertFalse(summary.output.endsWith("\n"))
    }

    @Test fun merelySimilarProgressLinesArePreserved() {
        val unknown = "> Task :app:compileDebugKotlin custom business data\n".repeat(5)
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle build", gradleLines(80) + unknown))
        assertEquals(78, summary.omittedLines)
        assertTrue(summary.output.endsWith(unknown))
    }

    @Test fun runsShorterThanFourAreRetained() {
        val shortRun = gradleLines(3)
        val output = shortRun + "separator\n" + gradleLines(80)
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle build", output))
        assertEquals(78, summary.omittedLines)
        assertTrue(summary.output.startsWith(shortRun + "separator\n"))
    }

    @Test fun failurePayloadContainingProgressIsRetainedVerbatim() {
        val failure = "FAILURE: assertion failed\nexpected log:\n" + gradleLines(80) +
            "actual log:\n" + gradleLines(80)
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle test", gradleLines(80) + failure))
        assertEquals(78, summary.omittedLines)
        assertTrue(summary.output.endsWith(failure))
    }

    @Test fun repeatedDiagnosticsAreNotFoldedWithoutProgress() {
        val output = "warning: repeated diagnostic must remain\n".repeat(100)
        assertNull(TerminalOutputCompressor.compress("gradle build", output))
    }

    @Test fun compilerPrefixesKeepFollowingDiagnosticLogSamplesVerbatim() {
        listOf("e: ", "w: ").forEach { prefix ->
            val diagnostic = prefix + "Main.kt:8: details\n" + gradleLines(40)
            val summary = requireNotNull(TerminalOutputCompressor.compress("gradle build", gradleLines(80) + diagnostic))
            assertEquals(78, summary.omittedLines)
            assertTrue(summary.output.endsWith(diagnostic))
        }
    }

    @Test fun proseContainingACommaIsNotMistakenForCsv() {
        val prose = "For more on this, please refer to the Gradle documentation.\n"
        val summary = requireNotNull(TerminalOutputCompressor.compress("gradle build", prose + gradleLines(80)))
        assertTrue(summary.output.startsWith(prose))
        assertEquals(78, summary.omittedLines)
    }

    @Test fun gradleDiagnosticCheckTaskNamesAreProgressUntilARealDiagnosticAppears() {
        val checkTask = "> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED\n"
        val suffix = "w: Main.kt:8: warning details\n" + checkTask + gradleLines(40)
        val summary = requireNotNull(TerminalOutputCompressor.compress("./gradlew assembleDebug", checkTask + gradleLines(80) + suffix))
        assertEquals(79, summary.omittedLines)
        assertTrue(summary.output.startsWith(checkTask))
        assertTrue(summary.output.endsWith(suffix))
    }

    private fun gradleLines(count: Int): String =
        (1..count).joinToString("") { "> Task :module$it:compileDebugKotlin UP-TO-DATE\n" }

    private fun assertSavings(original: String, summary: TerminalOutputCompressor.Summary) {
        val saved = original.length - summary.output.length
        assertTrue(original.length >= 2000)
        assertTrue(saved >= 256)
        assertTrue(saved.toLong() * 5 >= original.length.toLong())
        assertTrue(summary.omittedLines > 0)
    }
}
