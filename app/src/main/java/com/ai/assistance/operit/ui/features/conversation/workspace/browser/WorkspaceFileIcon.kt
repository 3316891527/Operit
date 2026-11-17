package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException

private sealed interface WorkspaceThumbnailState {
    object Loading : WorkspaceThumbnailState
    data class Ready(val bitmap: ImageBitmap) : WorkspaceThumbnailState
    object Failed : WorkspaceThumbnailState
}

/** 所有列表共享图标尺寸；目录和链接不触发内容读取，失败状态明确显示读取错误。 */
@Composable
internal fun WorkspaceFileIcon(
    location: WorkspaceLocation,
    directory: Boolean,
    version: String,
    symbolicLink: Boolean = false,
    thumbnails: Boolean = true,
    archiveFile: (suspend () -> File)? = null,
) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { 32.dp.roundToPx() }.coerceAtLeast(1)
    val typeIcon = remember(location.path) { workspaceFileTypeIcon(location.path) }
    val svg = location.path.substringAfterLast('.').equals("svg", true)
    val kind = remember(location.path, directory, symbolicLink, thumbnails) {
        if (directory || symbolicLink || !thumbnails) null else workspaceThumbnailKind(location.path)
    }
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        if (directory) Icon(Icons.Default.Folder, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        else if (kind == null) Image(painterResource(typeIcon), null, Modifier.size(28.dp))
        else key(location.key, version, pixels) {
            val currentArchiveFile by rememberUpdatedState(archiveFile)
            val state by produceState<WorkspaceThumbnailState>(WorkspaceThumbnailState.Loading, location.key, version, pixels) {
                try {
                    val bitmap = WorkspaceThumbnailLoader.load(context.applicationContext, location, version, kind, pixels, currentArchiveFile)
                    value = WorkspaceThumbnailState.Ready(bitmap.asImageBitmap())
                } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
                    AppLogger.e("WorkspaceThumbnail", "读取文件缩略图失败：${location.path}", failure)
                    value = WorkspaceThumbnailState.Failed
                }
            }
            when (val current = state) {
                WorkspaceThumbnailState.Loading -> Image(painterResource(typeIcon), null, Modifier.size(28.dp))
                is WorkspaceThumbnailState.Ready -> Image(current.bitmap, null, Modifier.size(32.dp).clip(RoundedCornerShape(5.dp))
                    .then(if (svg) Modifier.background(Color(0xFFE6E6E6)) else Modifier),
                    contentScale = if (kind == WorkspaceThumbnailKind.IMAGE && !svg) ContentScale.Crop else ContentScale.Fit)
                WorkspaceThumbnailState.Failed -> Icon(Icons.Default.ErrorOutline,
                    stringResource(R.string.cannot_open_file, location.path.substringAfterLast('/')),
                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}