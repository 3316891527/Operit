package com.ai.assistance.operit.ui.theme.transition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.hypot
import kotlin.math.max

/** 只有侧栏按钮主动调用，设置页和系统主题变化不创建转场。 */
class ThemeRevealController internal constructor(
    private val switchTheme: (Offset, suspend () -> Unit) -> Unit,
) {
    fun revealFrom(origin: Offset, changeTheme: suspend () -> Unit) = switchTheme(origin, changeTheme)
}

val LocalThemeRevealController = staticCompositionLocalOf<ThemeRevealController> {
    error("主题扩散宿主尚未安装")
}

private data class ThemeRevealSnapshot(val image: ImageBitmap, val origin: Offset)

@Composable
fun ThemeRevealHost(darkTheme: Boolean, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val currentDarkTheme by rememberUpdatedState(darkTheme)
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var snapshot by remember { mutableStateOf<ThemeRevealSnapshot?>(null) }
    var running by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val controller = remember(layer, scope) {
        ThemeRevealController { origin, changeTheme ->
            if (!running) {
                running = true
                scope.launch {
                    try {
                        val previousDarkTheme = currentDarkTheme
                        val image = layer.toImageBitmap()
                        progress.snapTo(0f)
                        snapshot = ThemeRevealSnapshot(image, origin - rootPosition)
                        changeTheme()
                        // 等待主题快照进入组合后再揭开旧画面；变化超时就释放截图和点击遮罩。
                        withTimeout(2_000) {
                            snapshotFlow { currentDarkTheme }.first { it != previousDarkTheme }
                        }
                        withFrameNanos { }
                        progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
                    } catch (error: TimeoutCancellationException) {
                        AppLogger.e("ThemeReveal", "侧栏主题变化超时", error)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLogger.e("ThemeReveal", "侧栏主题切换失败", error)
                    } finally {
                        snapshot = null
                        running = false
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalThemeRevealController provides controller) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { rootPosition = it.positionInRoot() }) {
            Box(Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }) { content() }
            snapshot?.let { captured ->
                Canvas(Modifier.fillMaxSize().pointerInput(captured) {
                    // 转场中吞掉第二次点击，防止旧截图上的导航操作修改当前页面。
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }) {
                    val origin = captured.origin
                    val radius = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y)) * progress.value
                    val oldArea = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(Rect(origin.x - radius, origin.y - radius, origin.x + radius, origin.y + radius))
                    }
                    // 圆内显示已更新的实时界面，圆外保留旧主题，形成从按钮向外的扩散。
                    clipPath(oldArea) { drawImage(captured.image) }
                }
            }
        }
    }
}
