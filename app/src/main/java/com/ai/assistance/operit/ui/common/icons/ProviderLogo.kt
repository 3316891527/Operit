package com.ai.assistance.operit.ui.common.icons

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.stats.normalizeProviderTypeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 提供商素材位于 model_logos/{providerTypeId}/，每个目录只放一个 SVG 或 PNG。 */
object ProviderLogoLoader {

    private const val LOGO_ROOT = "model_logos"

    /** 查找 provider 对应的 assets 内 logo 文件路径，不存在返回 null */
    fun findLogoAssetPath(context: Context, providerTypeId: String): String? {
        val normalizedProviderTypeId = normalizeProviderTypeId(providerTypeId)
        if (normalizedProviderTypeId.isBlank()) return null
        val dir = "$LOGO_ROOT/$normalizedProviderTypeId"
        return try {
            val files = context.assets.list(dir) ?: return null
            val file =
                files.firstOrNull {
                    it.endsWith(".svg", ignoreCase = true) ||
                        it.endsWith(".png", ignoreCase = true)
                } ?: return null
            "$dir/$file"
        } catch (_: Exception) {
            null
        }
    }

    /** 加载提供商素材，图片格式解析与缩放交给通用图片资源加载器。 */
    fun loadLogoBitmap(context: Context, providerTypeId: String, sizePx: Int): Bitmap? {
        val path = findLogoAssetPath(context, providerTypeId) ?: return null
        return ImageAssetLoader.load(context, path, sizePx)
    }

    // 彩色素材保留品牌配色；其余单色标识使用主题前景色以适配不同表面。
    private val originalColorProviderIds = setOf(
        "ALIYUN", "ANTHROPIC_GENERIC", "BAICHUAN", "BAIDU", "DEEPSEEK", "DOUBAO",
        "FOUR_ROUTER", "GEMINI_GENERIC", "GOOGLE", "IFLOW", "INFINIAI", "MINIMAX",
        "MISTRAL", "MOONSHOT", "NOVITA", "NVIDIA", "PPINFRA", "SILICONFLOW", "XUNFEI", "ZHIPU",
    )

    internal fun createPainter(
        bitmap: ImageBitmap,
        providerTypeId: String,
        foregroundColor: Color,
    ): Painter {
        val normalizedProviderTypeId = normalizeProviderTypeId(providerTypeId)
        val colorFilter =
            if (normalizedProviderTypeId in originalColorProviderIds) null
            else ColorFilter.tint(foregroundColor)
        return ProviderLogoPainter(bitmap, colorFilter)
    }
}

/** 将提供商着色封装进 Painter，使直接使用 Painter 的页面也得到一致的主题行为。 */
private class ProviderLogoPainter(bitmap: ImageBitmap, private val colorFilter: ColorFilter?) : Painter() {
    private val delegate = BitmapPainter(bitmap)
    override val intrinsicSize: Size = delegate.intrinsicSize

    override fun DrawScope.onDraw() {
        with(delegate) {
            draw(size = size, colorFilter = this@ProviderLogoPainter.colorFilter)
        }
    }
}

/** 异步加载提供商标识，并统一应用该素材的品牌颜色策略。 */
@Composable
fun rememberProviderLogoPainter(providerTypeId: String?, size: Dp = 24.dp): Painter? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by
        produceState<ImageBitmap?>(initialValue = null, context, providerTypeId, sizePx) {
            value =
                if (providerTypeId.isNullOrBlank()) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        ProviderLogoLoader.loadLogoBitmap(context, providerTypeId, sizePx)
                            ?.asImageBitmap()
                    }
                }
        }
    val foregroundColor = MaterialTheme.colorScheme.onSurface
    val image = bitmap ?: return null
    if (providerTypeId.isNullOrBlank()) return null
    return remember(image, providerTypeId, foregroundColor) {
        ProviderLogoLoader.createPainter(image, providerTypeId, foregroundColor)
    }
}

/** 复用供应商素材与现有加载逻辑；没有已登记素材时不增加占位图标。 */
@Composable
fun ProviderLogo(
    providerTypeId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val painter = rememberProviderLogoPainter(providerTypeId, size)
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = modifier.size(size),
        )
    }
}
