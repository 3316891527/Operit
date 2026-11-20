#!/usr/bin/env bash
set -u
set -o pipefail
operit_markdown_lib="${1:?请传入 Gradle 8.13 的 lib 目录}"
operit_markdown_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)" || exit 1
operit_markdown_stdlib=("$operit_markdown_lib"/kotlin-stdlib-[0-9]*.jar)
operit_markdown_annotations=("$operit_markdown_lib"/annotations-[0-9]*.jar)
[ "${#operit_markdown_stdlib[@]}" -eq 1 ] && [ -f "${operit_markdown_stdlib[0]}" ] || exit 1
[ "${#operit_markdown_annotations[@]}" -eq 1 ] && [ -f "${operit_markdown_annotations[0]}" ] || exit 1
operit_markdown_cp="${operit_markdown_stdlib[0]}:$operit_markdown_lib/junit-4.13.2.jar:$operit_markdown_lib/hamcrest-core-1.3.jar:${operit_markdown_annotations[0]}"
operit_markdown_output="$(mktemp -d "${TMPDIR:-/tmp}/operit-markdown-native.XXXXXX")" || exit 1
trap 'rm -rf -- "$operit_markdown_output"' EXIT
# 独立验证真实的全文语法与链接、表格逻辑，不创建应用或替代 Android UI。
java -Xmx512m -cp "$operit_markdown_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -classpath "$operit_markdown_cp" \
    -d "$operit_markdown_output/tests.jar" \
    "$operit_markdown_root/app/src/main/java/com/ai/assistance/operit/util/markdown/MarkdownProcessorType.kt" \
    "$operit_markdown_root/app/src/main/java/com/ai/assistance/operit/util/markdown/MarkdownBlockBoundary.kt" \
    "$operit_markdown_root/app/src/main/java/com/ai/assistance/operit/util/markdown/MarkdownSyntax.kt" \
    "$operit_markdown_root/app/src/main/java/com/ai/assistance/operit/util/markdown/MarkdownDocumentSyntax.kt" \
    "$operit_markdown_root/app/src/test/java/com/ai/assistance/operit/util/markdown/MarkdownSyntaxTest.kt" \
    "$operit_markdown_root/app/src/test/java/com/ai/assistance/operit/util/markdown/MarkdownBlockBoundaryTest.kt" || exit 1
java -Xmx128m -cp "$operit_markdown_output/tests.jar:$operit_markdown_cp" org.junit.runner.JUnitCore \
    com.ai.assistance.operit.util.markdown.MarkdownSyntaxTest \
    com.ai.assistance.operit.util.markdown.MarkdownBlockBoundaryTest