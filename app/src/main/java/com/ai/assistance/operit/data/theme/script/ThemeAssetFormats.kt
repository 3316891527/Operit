package com.ai.assistance.operit.data.theme.script

import java.util.Locale

/** JPG and JPEG are the same supported bitmap format at every resource entry point. */
object ThemeAssetFormats {
    val bitmaps = setOf("png", "jpg", "jpeg", "webp")
    val images = bitmaps + "svg"
    val fonts = setOf("ttf", "otf")
    val resources = images + fonts

    fun extension(path: String): String = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    fun isImage(path: String): Boolean = extension(path) in images
    fun isFont(path: String): Boolean = extension(path) in fonts
}
