package com.ai.assistance.operit.ui.common.image

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.imageLoader

// 普通界面沿用 Coil 单例，截图可在自己的组合树中指定软件位图加载器。
internal val LocalAppImageLoaderFactory =
    staticCompositionLocalOf<(Context) -> ImageLoader> { { context -> context.imageLoader } }

@Composable
internal fun currentAppImageLoader(): ImageLoader = LocalAppImageLoaderFactory.current(LocalContext.current)
