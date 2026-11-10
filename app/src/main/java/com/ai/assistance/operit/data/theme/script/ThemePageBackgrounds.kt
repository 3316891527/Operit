package com.ai.assistance.operit.data.theme.script

internal const val CHAT_PAGE_BACKGROUND_ID = "page.aiChat"

internal fun ThemeScriptSurface.hasBackgroundPaint(): Boolean =
    fillColor != null || fillGradient != null || layers.isNotEmpty()

/** 单页独立背景优先；用户未设置的页面统一跟随聊天主界面的背景。 */
internal fun pageBackgroundSource(pageId: String, own: ThemeScriptSurface?, chat: ThemeScriptSurface?): String? = when {
    own?.hasBackgroundPaint() == true -> pageId
    chat?.hasBackgroundPaint() == true -> CHAT_PAGE_BACKGROUND_ID
    else -> null // 全局背景由页面容器下面的共享背景层绘制。
}

/** 一次编辑清除所有非聊天页面的覆盖，保留全局、聊天背景和其余部件。 */
internal fun ThemeScriptAppearance.followChatBackgrounds(): ThemeScriptAppearance {
    val pages = ThemeStyleTargets.pageIds - CHAT_PAGE_BACKGROUND_ID
    return copy(
        surfaces = surfaces - pages,
        modes = modes.mapValues { (_, variant) -> variant.copy(surfaces = variant.surfaces - pages) },
    )
}
