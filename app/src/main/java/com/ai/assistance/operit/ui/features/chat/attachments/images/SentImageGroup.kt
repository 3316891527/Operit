package com.ai.assistance.operit.ui.features.chat.attachments.images

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImagePoolManager
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SentMessageImage(val id: String, val title: String? = null, val poolId: String? = null)
private data class SentImageState(val available: List<AttachmentPreviewImage>, val expiredCount: Int)

/** 共享图片组；加载时不显示过期标签，实际失效后才显示。 */
@Composable
internal fun SentImageGroup(sources: List<SentMessageImage>, enabled: Boolean, modifier: Modifier = Modifier) {
    if (sources.isEmpty()) return
    val context = LocalContext.current
    val revision by ImagePoolManager.revision.collectAsState()
    val state by produceState<SentImageState?>(initialValue = null, key1 = sources, key2 = revision) {
        value = withContext(Dispatchers.IO) {
            val images = sources.mapNotNull { source ->
                try {
                    val uri = if (source.poolId != null) {
                        ImagePoolManager.getPreviewFile(source.poolId)?.let(Uri::fromFile)
                    } else if (source.id.startsWith("content://")) {
                        val candidate = Uri.parse(source.id)
                        context.contentResolver.openAssetFileDescriptor(candidate, "r")?.use { candidate }
                    } else {
                        val file = if (source.id.startsWith("file://")) File(Uri.parse(source.id).path.orEmpty()) else File(source.id)
                        file.takeIf { it.isFile }?.let(Uri::fromFile)
                    }
                    uri?.let { AttachmentPreviewImage(it, source.title) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("SentImageGroup", "读取已发送图片失败: ${source.id}", e)
                    null
                }
            }
            SentImageState(images, sources.size - images.size)
        }
    }
    var previewVisible by remember(sources) { mutableStateOf(false) }
    val images = state?.available.orEmpty()
    if (enabled && previewVisible && images.isNotEmpty()) {
        AttachmentImagePreviewDialog(images = images, initialImageUri = images.first().uri, onDismiss = { previewVisible = false })
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state == null) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) }
        } else if (images.isNotEmpty()) {
            val layers = images.take(3)
            Box(Modifier.width((112 + (layers.size - 1) * 10).dp).height(122.dp).clickable(enabled = enabled) { previewVisible = true }) {
                layers.indices.reversed().forEach { index ->
                    Surface(
                        modifier = Modifier.offset(x = (index * 10).dp, y = (index * 4).dp).size(112.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 1.dp,
                    ) {
                        AsyncImage(model = layers[index].uri, contentDescription = layers[index].title, contentScale = ContentScale.Crop)
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ) {
                    Text(
                        stringResource(R.string.sent_image_count, sources.size),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        state?.expiredCount?.takeIf { it > 0 }?.let { count ->
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    stringResource(R.string.sent_image_expired_count, count),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
