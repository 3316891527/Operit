package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import kotlin.random.Random

/** 只识别协议定义的完整标签，素材分类使用仓库允许的字符。 */
internal val waifuEmotionDirective = Regex("""<emotion>\s*([a-z0-9_]+)\s*</emotion>""")

internal fun waifuEmotionCategory(xml: String): String? =
    waifuEmotionDirective.matchEntire(xml.trim())?.groupValues?.get(1)

/** 每轮发送固定素材目录；相同分类的每次出现各自选图，快照重算时复用选择。 */
internal class WaifuEmotionImages(
    private val urlsByCategory: Map<String, List<String>>,
    private val chooseIndex: (Int) -> Int = { Random.nextInt(it) },
) {
    private val chosenByCategory = mutableMapOf<String, MutableList<String>>()

    fun hasCategory(category: String): Boolean = !urlsByCategory[category].isNullOrEmpty()

    fun snapshot(): Snapshot = Snapshot()

    inner class Snapshot {
        private val occurrences = mutableMapOf<String, Int>()

        fun render(category: String): String? {
            val urls = urlsByCategory[category] ?: return null
            if (urls.isEmpty()) return null
            val occurrence = occurrences.getOrDefault(category, 0)
            occurrences[category] = occurrence + 1
            val chosen = chosenByCategory.getOrPut(category) { mutableListOf() }
            if (occurrence == chosen.size) chosen += urls[chooseIndex(urls.size)]
            // 表情是装饰图片，不让既有的朗读清理把分类名当作正文。
            return "![](${chosen[occurrence]})"
        }
    }
}
