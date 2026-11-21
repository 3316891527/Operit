#include "StreamOperators.h"
#include <algorithm>
#include <cstdlib>
#include <iostream>
#include <string>
#include <utility>
#include <vector>

using namespace streamnative;
using Groups = std::vector<std::pair<int, std::u16string>>;

static Groups parse(const std::u16string& source, bool block, size_t split) {
    MarkdownSession* session = block ? createMarkdownBlockSession() : createMarkdownInlineSession();
    Groups groups;
    bool boundary = true;
    auto push = [&](size_t start, size_t count) {
        auto segments = markdownSessionPush(session, reinterpret_cast<const jchar*>(source.data() + start), static_cast<int>(count));
        for (const auto& segment : segments) {
            if (segment.type < 0) { boundary = true; continue; }
            if (segment.start < 0 || segment.end < segment.start || static_cast<size_t>(segment.end) > source.size()) std::abort();
            if (groups.empty() || boundary || groups.back().first != segment.type) groups.push_back({segment.type, {}});
            groups.back().second += source.substr(segment.start, segment.end - segment.start);
            boundary = false;
        }
    };
    push(0, split);
    push(split, source.size() - split);
    destroyMarkdownSession(session);
    return groups;
}

static void require(bool condition, const char* description) {
    if (!condition) { std::cerr << "FAIL: " << description << '\n'; std::exit(1); }
}

static bool contains(const Groups& groups, int tag, const std::u16string& text) {
    return std::any_of(groups.begin(), groups.end(), [&](const auto& group) { return group.first == tag && group.second == text; });
}

int main() {
    const std::vector<std::pair<std::u16string, bool>> fixtures = {
        {u"\\*literal\\* **bold**\n", false},
        {u"\\(x^2\\) and $a_1$\n", false},
        {u"==highlight== H~2~O x^2^ ~~deleted~~\n", false},
        {u"[name](https://example.com/a_(b) \"title\") tail\n", false},
        {u"![image](<images/a b.png> 'title') after\n", true},
        {u"- [x] task\n  - nested\n12) ordered\n", true},
        {u"> **quote**\n> > nested\n\nAfter\n", true},
        {u"```text\n\\*literal\\* ==code==\n```\nAfter\n", true},
        {u"[ordinary] text and [link](a.png)\n", false},
        {u"literal \\<tool/> and \\*stars\\*\n", true},
    };
    size_t chunkChecks = 0;
    for (const auto& [source, block] : fixtures) {
        const auto expected = parse(source, block, source.size());
        for (size_t split = 0; split <= source.size(); ++split) {
            require(parse(source, block, split) == expected, "分片边界必须保持完整解析结果");
            chunkChecks++;
        }
    }
    const auto escaped = parse(fixtures[0].first, false, 0);
    require(contains(escaped, 19, u"\\*"), "转义应成为独立的完整词元");
    require(contains(escaped, 9, u"bold"), "转义后的普通粗体仍能识别");
    const auto maths = parse(fixtures[1].first, false, 0);
    require(contains(maths, 16, u"\\(x^2\\)"), "数学圆括号优先于转义");
    require(contains(maths, 16, u"a_1"), "美元符号公式仍可识别");
    const auto styles = parse(fixtures[2].first, false, 0);
    require(contains(styles, 20, u"==highlight=="), "高亮边界正确");
    require(contains(styles, 21, u"~2~"), "下标边界正确");
    require(contains(styles, 22, u"^2^"), "上标边界正确");
    require(contains(styles, 14, u"deleted"), "删除线不能被单波浪线抢占");
    require(contains(parse(fixtures[3].first, false, 0), 12, u"[name](https://example.com/a_(b) \"title\")"), "链接目标括号和标题必须完整");
    require(contains(parse(fixtures[4].first, true, 0), 13, u"![image](<images/a b.png> 'title')"), "图片目标和标题必须完整");
    require(contains(parse(fixtures[5].first, true, 0), 4, u"  - nested\n"), "列表缩进必须保留");
    const auto quote = parse(fixtures[6].first, true, 0);
    require(std::any_of(quote.begin(), quote.end(), [](const auto& value) { return value.first == 1 && value.second.find(u"> > nested") != std::u16string::npos; }), "二级引用前缀必须保留");
    require(contains(parse(fixtures[8].first, false, 0), 12, u"[link](a.png)"), "普通方括号不能吞掉后续链接");
    const auto escapedHtml = parse(fixtures[9].first, true, 0);
    require(std::none_of(escapedHtml.begin(), escapedHtml.end(), [](const auto& value) { return value.first == 8; }), "转义的 HTML 起始符不能变成 XML");
    std::cout << "Native Markdown: " << fixtures.size() << " fixtures, " << chunkChecks << " chunk boundaries passed\n";
}