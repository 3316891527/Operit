package com.ai.assistance.operit.ui.features.conversation.workspace.svg

import android.content.Context
import android.graphics.Bitmap
import com.ai.assistance.operit.ui.common.icons.ImageAssetLoader
import com.ai.assistance.operit.ui.common.icons.SvgImageAsset
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboardItem
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserOperations
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.isValidWorkspaceEntryName
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.workspaceChild
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal enum class WorkspaceSvgFormat(val extension: String) { XML("xml"), PNG("png") }
internal fun isWorkspaceSvg(path: String): Boolean = path.substringAfterLast('.').equals("svg", true)
internal fun validSvgExportSize(width: Int?, height: Int?): Boolean =
    width != null && height != null && width in 1..4096 && height in 1..4096 && width.toLong() * height <= SvgImageAsset.MAX_PIXELS

/** 转换先写入独立暂存目录，成功后复用现有跨环境传输，不修改源文件或覆盖目标。 */
internal object WorkspaceSvgExport {
    suspend fun export(context: Context, bytes: ByteArray, format: WorkspaceSvgFormat, name: String,
        destination: WorkspaceLocation, width: Int, height: Int): WorkspaceLocation = withContext(Dispatchers.IO) {
        require(isValidWorkspaceEntryName(name))
        if (format == WorkspaceSvgFormat.PNG) require(validSvgExportSize(width, height))
        val location = workspaceChild(destination, "$name.${format.extension}")
        val operations = FileBrowserOperations(context)
        operations.requireNewDestination(location.path, location.environment)
        val root = File(context.cacheDir, "workspace_svg_exports").also { check(it.isDirectory || it.mkdirs()) }
        val staging = File(root, UUID.randomUUID().toString()).also { check(it.mkdir()) }
        try {
            val output = File(staging, "$name.${format.extension}")
            when (format) {
                WorkspaceSvgFormat.XML -> output.writeText(SvgVectorConverter.convert(bytes), Charsets.UTF_8)
                WorkspaceSvgFormat.PNG -> {
                    val bitmap = bytes.inputStream().use(ImageAssetLoader::readSvg).render(width, height)
                    try { output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                    finally { bitmap.recycle() }
                }
            }
            currentCoroutineContext().ensureActive()
            operations.transfer(listOf(FileBrowserClipboardItem(output.path, "android", false, false)), destination.path, destination.environment)
            location
        } finally {
            staging.deleteRecursively()
        }
    }
}
