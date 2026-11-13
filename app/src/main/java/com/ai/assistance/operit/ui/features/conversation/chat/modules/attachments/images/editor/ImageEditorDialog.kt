package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class ImageEditorTextTarget(val index: Int?, val position: ImageEditPoint, val quarterTurns: Int)

/** Editing is transactional: only Done or Save commits a flattened replacement attachment. */
@Composable
internal fun ImageEditorDialog(
    imageUri: Uri,
    onApply: (File) -> Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(imageUri) { mutableStateOf<String?>(null) }
    var history by remember(imageUri) { mutableStateOf(ImageEditHistory()) }
    var tool by remember { mutableStateOf(ImageEditTool.PEN) }
    var drawingTool by remember { mutableStateOf(ImageEditTool.PEN) }
    var brushMenuExpanded by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(0xFFFF3B30.toInt()) }
    var strokeWidth by remember { mutableStateOf(0.009f) }
    var saving by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var textTarget by remember { mutableStateOf<ImageEditorTextTarget?>(null) }
    var text by remember { mutableStateOf("") }
    var textSize by remember { mutableStateOf(0.06f) }
    var textColor by remember { mutableStateOf(color) }

    LaunchedEffect(imageUri) {
        try {
            bitmap = loadImageEditBitmap(context, imageUri, preview = true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            AppLogger.e("ImageEditor", "Could not load image", failure)
            error = context.getString(R.string.image_editor_load_failed)
        }
    }
    DisposableEffect(bitmap) {
        val loaded = bitmap
        onDispose { loaded?.recycle() }
    }

    val requestClose: () -> Unit = {
        if (!saving) {
            if (history.changed) confirmLeave = true else onDismiss()
        }
    }
    val save: () -> Unit = {
        if (!saving) {
            if (!history.changed) onDismiss()
            else {
                saving = true
                error = null
                val document = history.current
                scope.launch {
                    try {
                        val output = saveImageEdit(context, imageUri, document)
                        var accepted = false
                        try { accepted = onApply(output) }
                        finally { if (!accepted) output.delete() }
                        if (accepted) onDismiss()
                        else error = context.getString(R.string.image_editor_attachment_changed)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        AppLogger.e("ImageEditor", "Could not save image", failure)
                        error = failure.message ?: context.getString(R.string.image_editor_save_failed)
                    } finally { saving = false }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.image_editor_title), color = Color.White, modifier = Modifier.weight(1f).padding(8.dp))
                IconButton(onClick = { history = history.redo() }, enabled = !saving && history.following.isNotEmpty()) {
                    Icon(Icons.Default.Redo, stringResource(R.string.image_editor_redo), tint = if (history.following.isEmpty()) Color.Gray else Color.White)
                }
                IconButton(onClick = requestClose, enabled = !saving) {
                    Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val loaded = bitmap
                if (loaded != null) {
                    ImageEditorCanvas(
                        image = loaded,
                        document = history.current,
                        tool = tool,
                        color = color,
                        width = if (tool == ImageEditTool.ERASER || tool == ImageEditTool.MOSAIC) strokeWidth * 3f else strokeWidth,
                        enabled = !saving && textTarget == null && !brushMenuExpanded,
                        onCommit = { history = history.commit(it) },
                        onText = { index, position ->
                            val existing = index?.let { history.current.marks[it] as ImageEditMark.Text }
                            text = existing?.text.orEmpty()
                            textSize = existing?.size ?: 0.06f
                            textColor = existing?.color ?: color
                            textTarget = ImageEditorTextTarget(index, existing?.position ?: position, existing?.quarterTurns ?: 0)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (error == null) CircularProgressIndicator(color = Color.White)
                if (saving) CircularProgressIndicator(color = Color.White)
            }
            error?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            Text(
                stringResource(if (tool == ImageEditTool.CROP) R.string.image_editor_crop_hint else R.string.image_editor_gesture_hint),
                color = Color.LightGray, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val primaryTools = listOf(
                ImageEditTool.MOVE,
                ImageEditTool.PEN,
                ImageEditTool.MOSAIC,
                ImageEditTool.TEXT,
                ImageEditTool.CROP,
                ImageEditTool.ERASER,
            )
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).heightIn(min = 96.dp),
            ) {
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 88.dp)) {
                    if (tool.isDrawingTool()) {
                        EditorBrushControls(
                            selected = tool,
                            color = color,
                            enabled = !saving,
                            expanded = brushMenuExpanded,
                            onExpandedChange = { brushMenuExpanded = it },
                            onToolSelected = { drawingTool = it; tool = it },
                            onColor = { color = it },
                        )
                    }
                    if (tool.isDrawingTool() || tool == ImageEditTool.MOSAIC || tool == ImageEditTool.ERASER) {
                        EditorThinSlider(
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = 0.003f..0.04f,
                            label = stringResource(R.string.image_editor_width),
                            enabled = !saving,
                            thumbColor = Color.White,
                            activeColor = Color(0xFFFF2D55),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    if (tool == ImageEditTool.CROP) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            val actionColors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.35f),
                            )
                            TextButton(
                                onClick = { history = history.commit(history.current.restoreGeometry()) },
                                enabled = !saving && history.current.geometryChanged,
                                colors = actionColors,
                            ) { Text(stringResource(R.string.image_editor_restore)) }
                            TextButton(
                                onClick = { history = history.commit(history.current.rotateClockwise()) },
                                enabled = !saving && bitmap != null,
                                colors = actionColors,
                            ) { Text(stringResource(R.string.image_editor_rotate)) }
                        }
                    }
                    EditorToolRow(
                        tools = primaryTools,
                        selected = if (tool.isDrawingTool()) ImageEditTool.PEN else tool,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                        onToolSelected = { entry ->
                            if (entry == ImageEditTool.PEN) {
                                if (tool.isDrawingTool()) brushMenuExpanded = !brushMenuExpanded
                                else { tool = drawingTool; brushMenuExpanded = false }
                            } else {
                                tool = entry
                                brushMenuExpanded = false
                            }
                        },
                    )
                }
                Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { history = history.undo() }, enabled = !saving && history.previous.isNotEmpty()) {
                        Icon(Icons.Default.Undo, stringResource(R.string.image_editor_undo), tint = if (history.previous.isEmpty()) Color.Gray else Color.White)
                    }
                    Button(onClick = save, enabled = bitmap != null && !saving) { Text(stringResource(R.string.image_editor_done)) }
                }
            }
        }

        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                title = { Text(stringResource(R.string.image_editor_unsaved_title)) },
                text = { Text(stringResource(R.string.image_editor_unsaved_message)) },
                dismissButton = {
                    TextButton(onClick = { confirmLeave = false; onDismiss() }) { Text(stringResource(R.string.image_editor_discard)) }
                },
                confirmButton = {
                    TextButton(onClick = { confirmLeave = false; save() }) { Text(stringResource(R.string.save)) }
                },
            )
        }
        textTarget?.let { target ->
            ImageEditorTextDialog(
                canDelete = target.index != null,
                text = text,
                onTextChange = { text = it },
                textSize = textSize,
                onTextSizeChange = { textSize = it },
                textColor = textColor,
                onTextColorChange = { textColor = it },
                onDismiss = { textTarget = null },
                onDelete = {
                    history = history.commit(history.current.copy(marks = history.current.marks.filterIndexed { index, _ -> index != target.index }))
                    textTarget = null
                },
                onConfirm = {
                    val label = ImageEditMark.Text(text.trim(), target.position, textColor, textSize, target.quarterTurns)
                    val marks = if (target.index == null) history.current.marks + label
                        else history.current.marks.mapIndexed { index, mark -> if (index == target.index) label else mark }
                    history = history.commit(history.current.copy(marks = marks))
                    textTarget = null
                },
            )
        }
    }
}

@Composable
private fun ImageEditorTextDialog(
    canDelete: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    textSize: Float,
    onTextSizeChange: (Float) -> Unit,
    textColor: Int,
    onTextColorChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp).imePadding(),
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.image_editor_text),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close), tint = colors.onSurface)
                    }
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isBlank()) {
                                Text(stringResource(R.string.image_editor_text_hint), color = colors.onSurfaceVariant)
                            }
                            innerTextField()
                        }
                    },
                )
                EditorThinSlider(
                    value = textSize,
                    onValueChange = onTextSizeChange,
                    valueRange = 0.02f..0.14f,
                    label = stringResource(R.string.image_editor_text_size),
                )
                EditorColors(textColor, true, onColor = onTextColorChange)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canDelete) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                        ) { Text(stringResource(R.string.delete)) }
                    }
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
                    ) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = onConfirm,
                        enabled = text.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    ) { Text(stringResource(R.string.image_editor_done)) }
                }
            }
        }
    }
}
