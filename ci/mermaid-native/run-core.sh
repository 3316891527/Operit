#!/usr/bin/env bash
set -u
set -o pipefail
operit_mermaid_lib="${1:?请传入 Gradle 8.13 的 lib 目录}"
operit_mermaid_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)" || exit 1
operit_mermaid_source="$operit_mermaid_root/app/src/main/java/com/ai/assistance/operit/ui/common/markdown/mermaid"
operit_mermaid_stdlib=("$operit_mermaid_lib"/kotlin-stdlib-[0-9]*.jar)
operit_mermaid_annotations=("$operit_mermaid_lib"/annotations-[0-9]*.jar)
[ "${#operit_mermaid_stdlib[@]}" -eq 1 ] && [ -f "${operit_mermaid_stdlib[0]}" ] || exit 1
[ "${#operit_mermaid_annotations[@]}" -eq 1 ] && [ -f "${operit_mermaid_annotations[0]}" ] || exit 1
operit_mermaid_cp="${operit_mermaid_stdlib[0]}:$operit_mermaid_lib/junit-4.13.2.jar:$operit_mermaid_lib/hamcrest-core-1.3.jar:${operit_mermaid_annotations[0]}"
operit_mermaid_output="$(mktemp -d "${TMPDIR:-/tmp}/operit-mermaid-native.XXXXXX")" || exit 1
trap 'rm -rf -- "$operit_mermaid_output"' EXIT
# 只编译生产解析、布局、视口及其真实测试，不依赖 Android SDK 或 UI 桩。
java -Xmx512m -cp "$operit_mermaid_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -classpath "$operit_mermaid_cp" \
    -d "$operit_mermaid_output/tests.jar" \
    "$operit_mermaid_source/MermaidModel.kt" "$operit_mermaid_source/MermaidParser.kt" \
    "$operit_mermaid_source/MermaidGraphParser.kt" "$operit_mermaid_source/MermaidChartParser.kt" \
    "$operit_mermaid_source/MermaidLayout.kt" "$operit_mermaid_source/MermaidTimelineLayout.kt" \
    "$operit_mermaid_root/app/src/test/java/com/ai/assistance/operit/ui/common/markdown/mermaid/NativeMermaidTest.kt" || exit 1
java -Xmx128m -cp "$operit_mermaid_output/tests.jar:$operit_mermaid_cp" org.junit.runner.JUnitCore \
    com.ai.assistance.operit.ui.common.markdown.mermaid.NativeMermaidTest