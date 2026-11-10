package com.ai.assistance.operit.ui.theme.script

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.*
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.theme.script.ThemeScriptSurface
import com.ai.assistance.operit.data.theme.script.forState
import java.util.Locale

/** 一个部件只生成一组原生动画值，正文、容器、边框与装饰共用同一份结果。 */
internal val LocalThemeAnimatedSurfaces = compositionLocalOf<Map<String, ThemeScriptSurface>> { emptyMap() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@UiComposable
internal fun ThemeSurfaceVisualScope(id: String, content: @Composable @UiComposable () -> Unit) {
    if (id in LocalThemeAnimatedSurfaces.current) {
        content()
        return
    }
    val base = baseThemeSurface(id)
    if (base == null) {
        content()
        return
    }
    // 工程、角色或模式修改应立即显示新外观；只有同一声明的原生状态变化参与动画。
    key(base) {
        val state = LocalThemeSurfaceStates.current[id] ?: "default"
        val resolved = animatedThemeSurface(id, base, state)
        CompositionLocalProvider(LocalThemeAnimatedSurfaces provides (LocalThemeAnimatedSurfaces.current + (id to resolved))) {
            val ripple = base.ripple
            if (ripple == null) {
                content()
            } else {
                val configuration = remember(ripple) { if (ripple.enabled) RippleConfiguration(color = ripple.color?.let(::themeColor) ?: Color.Unspecified) else null }
                CompositionLocalProvider(LocalRippleConfiguration provides configuration, content = content)
            }
        }
    }
}

@Composable
private fun animatedThemeSurface(id: String, base: ThemeScriptSurface, state: String): ThemeScriptSurface {
    val duration = base.transition?.durationMillis ?: return base.forState(state)
    val transition = updateTransition(state, label = "theme.$id")
    val opacity by transition.animateFloat(transitionSpec = { tween(duration) }, label = "opacity") { base.forState(it).opacity }
    val width by transition.animateFloat(transitionSpec = { tween(duration) }, label = "strokeWidth") { base.forState(it).strokeWidth }
    val fill = transition.surfaceColor(base.fillColor, duration, "fillColor") { base.forState(it).fillColor }
    val content = transition.surfaceColor(base.contentColor, duration, "contentColor") { base.forState(it).contentColor }
    val stroke = transition.surfaceColor(base.strokeColor, duration, "strokeColor") { base.forState(it).strokeColor }
    return base.forState(state).copy(opacity = opacity, strokeWidth = width, fillColor = fill, contentColor = content, strokeColor = stroke)
}

/** 只有明确声明的颜色参与插值；继承的原生颜色和渐变不被虚构为某个起始色。 */
@Composable
private fun Transition<String>.surfaceColor(base: String?, duration: Int, label: String, value: (String) -> String?): String? {
    if (base == null) return value(targetState)
    val color by animateColor(transitionSpec = { tween(duration) }, label = label) { state -> themeColor(requireNotNull(value(state))) }
    return String.format(Locale.ROOT, "#%08X", color.toArgb())
}
