package com.ai.assistance.operit.ui.features.chat.attachments.images

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ai.assistance.operit.R

/** 附件与相册共用的全屏图片预览，关闭预览不会改变附件或选择状态。 */
@Composable
fun AttachmentImagePreviewDialog(
    imageUri: Uri,
    title: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var viewport by remember(imageUri) { mutableStateOf(Size.Zero) }
    var imageSize by remember(imageUri) { mutableStateOf(Size.Zero) }
    var transform by remember(imageUri, viewport) { mutableStateOf(ImagePreviewTransform()) }
    var loading by remember(imageUri) { mutableStateOf(true) }
    var failed by remember(imageUri) { mutableStateOf(false) }
    val request = remember(context, imageUri) {
        // 预览只解码当前图片，并限制解码尺寸，避免超大原图直接占满内存。
        ImageRequest.Builder(context).data(imageUri).size(4096).build()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(imageUri, viewport, imageSize) {
                        detectTapGestures(
                            onDoubleTap = { focus ->
                                if (viewport.width > 0f && viewport.height > 0f &&
                                    imageSize.width > 0f && imageSize.height > 0f
                                ) {
                                    transform = transformImagePreview(
                                        current = transform,
                                        targetScale = if (transform.scale > 1f) 1f else 3f,
                                        focus = focus,
                                        pan = Offset.Zero,
                                        viewport = viewport,
                                        image = imageSize,
                                    )
                                }
                            },
                        )
                    }
                    .pointerInput(imageUri, viewport, imageSize) {
                        detectTransformGestures { focus, pan, zoom, _ ->
                            if (viewport.width > 0f && viewport.height > 0f &&
                                imageSize.width > 0f && imageSize.height > 0f
                            ) {
                                transform = transformImagePreview(
                                    current = transform,
                                    targetScale = transform.scale * zoom,
                                    focus = focus,
                                    pan = pan,
                                    viewport = viewport,
                                    image = imageSize,
                                )
                            }
                        }
                    },
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = stringResource(R.string.image_preview),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offset.x
                        translationY = transform.offset.y
                    },
                    onLoading = { loading = true; failed = false },
                    onSuccess = {
                        loading = false
                        failed = false
                        imageSize = it.painter.intrinsicSize
                    },
                    onError = { loading = false; failed = true },
                )
            }
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            if (failed) {
                Text(
                    text = stringResource(R.string.attachment_image_preview_failed),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape),
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White)
                }
            }
        }
    }
}