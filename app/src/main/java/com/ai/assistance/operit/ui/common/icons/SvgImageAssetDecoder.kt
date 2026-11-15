package com.ai.assistance.operit.ui.common.icons

import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.Options

/** 按请求显式接入的 SVG 解码器；预览和缩略图共用图片资源层，保持各自的尺寸上限。 */
class SvgImageAssetDecoder private constructor(
    private val result: SourceResult,
    private val options: Options,
    private val maxSidePx: Int,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bitmap = result.source.source().inputStream().use { input ->
            ImageAssetLoader.readSvg(input).renderFitted(maxSidePx)
        }
        return DecodeResult(BitmapDrawable(options.context.resources, bitmap), isSampled = true)
    }

    class Factory(private val maxSidePx: Int) : Decoder.Factory {
        init { require(maxSidePx > 0 && maxSidePx.toLong() * maxSidePx <= SvgImageAsset.MAX_PIXELS) }
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder =
            SvgImageAssetDecoder(result, options, maxSidePx)
    }
}
