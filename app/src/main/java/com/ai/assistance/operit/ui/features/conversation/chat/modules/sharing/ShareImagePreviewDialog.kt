package com.ai.assistance.operit.ui.features.conversation.chat.modules.sharing

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.ImagePreviewTransform
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.transformImagePreview
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ShareImagePreviewDialog(
    imageUri: Uri?,
    isGenerating: Boolean,
    thinkingExpanded: Boolean,
    expandThinkToolsGroups: Boolean,
    includeBackground: Boolean,
    borderWidth: Float,
    onThinkingExpandedChange: (Boolean) -> Unit,
    onExpandThinkToolsGroupsChange: (Boolean) -> Unit,
    onIncludeBackgroundChange: (Boolean) -> Unit,
    onBorderWidthChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.save_image), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        var viewport by remember(imageUri) { mutableStateOf(Size.Zero) }
                        var imageSize by remember(imageUri) { mutableStateOf(Size.Zero) }
                        var transform by remember(imageUri, viewport) {
                            mutableStateOf(ImagePreviewTransform())
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .clipToBounds()
                                .onSizeChanged {
                                    viewport = Size(it.width.toFloat(), it.height.toFloat())
                                }
                                .pointerInput(imageUri, viewport, imageSize) {
                                    // Handle gestures in viewport coordinates, outside the scaled layer.
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
                                model = imageUri,
                                contentDescription = stringResource(R.string.image_preview),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = transform.scale
                                    scaleY = transform.scale
                                    translationX = transform.offset.x
                                    translationY = transform.offset.y
                                },
                                onSuccess = { imageSize = it.painter.intrinsicSize },
                            )
                        }
                    }

                    if (isGenerating) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.share_preview_generating), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val controlScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(controlScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.share_preview_expand_thinking), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = thinkingExpanded, onCheckedChange = onThinkingExpandedChange)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.share_preview_expand_tools_groups), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = expandThinkToolsGroups, onCheckedChange = onExpandThinkToolsGroupsChange)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.share_preview_include_background), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = includeBackground, onCheckedChange = onIncludeBackgroundChange)
                    }
                    Text(
                        stringResource(R.string.share_preview_border_width, borderWidth),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = borderWidth,
                        onValueChange = onBorderWidthChange,
                        valueRange = 0f..6f,
                        steps = 5
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onShare, enabled = imageUri != null && !isGenerating) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.share))
                    }
                    Button(onClick = onSave, enabled = imageUri != null && !isGenerating) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.save_image))
                    }
                }
            }
        }
    }
}

suspend fun saveShareImageToGallery(context: android.content.Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val fileName = "operit_share_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Operit")
                }
                val targetUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext false
                context.contentResolver.openInputStream(uri)?.use { input ->
                    context.contentResolver.openOutputStream(targetUri)?.use { output ->
                        input.copyTo(output)
                    } ?: return@withContext false
                } ?: return@withContext false
                true
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val outputDir = File(picturesDir, "Operit").apply { mkdirs() }
                val outputFile = File(outputDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outputFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext false
                true
            }
        } catch (e: Exception) {
            AppLogger.e("ChatScreenContent", "Failed to save share image", e)
            false
        }
    }
