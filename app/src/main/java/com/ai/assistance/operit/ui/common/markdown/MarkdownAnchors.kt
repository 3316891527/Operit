@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.markdownHeadingSlug
import java.net.URLDecoder

internal class MarkdownAnchorRegistry {
    private val targets = mutableMapOf<String, BringIntoViewRequester>()
    fun register(names: List<String>, target: BringIntoViewRequester) { names.forEach { targets.putIfAbsent(it, target) } }
    fun unregister(names: List<String>, target: BringIntoViewRequester) { names.forEach { if (targets[it] === target) targets.remove(it) } }
    suspend fun reveal(name: String) { targets[URLDecoder.decode(name, "UTF-8")]?.bringIntoView() }
}

internal val LocalMarkdownAnchorRegistry = compositionLocalOf<MarkdownAnchorRegistry?> { null }

/** 标题、脚注与引用位置共用滚动目标；嵌套内容继承文档的注册表。 */
internal fun markdownAnchorNames(nodes: List<MarkdownNodeStable>, headings: List<MarkdownHeading>): Map<Int, List<String>> {
    val names = mutableMapOf<Int, MutableList<String>>()
    val duplicates = mutableMapOf<String, Int>()
    headings.forEach { heading ->
        val slug = markdownHeadingSlug(heading.title)
        val count = duplicates[slug] ?: 0
        duplicates[slug] = count + 1
        names.getOrPut(heading.nodeIndex) { mutableListOf() } += slug + if (count == 0) "" else "-$count"
    }
    fun source(node: MarkdownNodeStable): String = node.content + node.children.joinToString("") { source(it) }
    nodes.forEachIndexed { index, node ->
        if (node.type == MarkdownProcessorType.FOOTNOTE) {
            names.getOrPut(index) { mutableListOf() } += "fn-" + node.content.substringBefore('\n')
        } else if (node.type !in setOf(MarkdownProcessorType.CODE_BLOCK, MarkdownProcessorType.XML_BLOCK, MarkdownProcessorType.HTML_BLOCK, MarkdownProcessorType.BLOCK_LATEX)) {
            Regex("#fn-([0-9a-f]+)").findAll(source(node)).forEach {
                names.getOrPut(index) { mutableListOf() } += "fnref-" + it.groupValues[1]
            }
        }
    }
    return names
}

@Composable
internal fun markdownAnchorModifier(registry: MarkdownAnchorRegistry, names: List<String>): Modifier {
    if (names.isEmpty()) return Modifier
    val target = remember { BringIntoViewRequester() }
    DisposableEffect(registry, names, target) {
        registry.register(names, target)
        onDispose { registry.unregister(names, target) }
    }
    return Modifier.bringIntoViewRequester(target)
}