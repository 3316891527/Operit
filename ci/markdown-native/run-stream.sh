#!/usr/bin/env bash
set -u
set -o pipefail
operit_markdown_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)" || exit 1
operit_markdown_jdk="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")" || exit 1
operit_markdown_output="$(mktemp -d "${TMPDIR:-/tmp}/operit-markdown-stream.XXXXXX")" || exit 1
trap 'rm -rf -- "$operit_markdown_output"' EXIT
# 编译生产原生流解析器并遍历每个分片位置，验证 JNI 使用的同一套索引逻辑。
g++ -std=c++17 -O1 -Wall -Wextra \
    -I "$operit_markdown_jdk/include" -I "$operit_markdown_jdk/include/linux" \
    -I "$operit_markdown_root/app/src/main/cpp/streamnative" \
    "$operit_markdown_root/ci/markdown-native/stream-test.cpp" \
    "$operit_markdown_root/app/src/main/cpp/streamnative/StreamOperators.cpp" \
    "$operit_markdown_root/app/src/main/cpp/streamnative/plugins/StreamMarkdownPlugin.cpp" \
    "$operit_markdown_root/app/src/main/cpp/streamnative/plugins/StreamXmlPlugin.cpp" \
    -o "$operit_markdown_output/check" || exit 1
"$operit_markdown_output/check"