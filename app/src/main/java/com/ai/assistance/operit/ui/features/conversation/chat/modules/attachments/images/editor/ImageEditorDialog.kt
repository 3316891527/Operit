package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class ImageEditorTextTarget(val index: Int?, val position: ImageEditPoint)

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
                        enabled = !saving && textTarget == null,
                        onCommit = { history = history.commit(it) },
                        onText = { index, position ->
                            val existing = index?.let { history.current.marks[it] as ImageEditMark.Text }
                            text = existing?.text.orEmpty()
                            textSize = existing?.size ?: 0.06f
                            textColor = existing?.color ?: color
                            textTarget = ImageEditorTextTarget(index, existing?.position ?: position)
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
            val drawingTools = listOf(
                ImageEditTool.PEN,
                ImageEditTool.RECTANGLE,
                ImageEditTool.OVAL,
                ImageEditTool.ARROW,
            )
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                    .heightIn(min = if (tool.isDrawingTool()) 146.dp else 100.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(end = 88.dp)) {
                    if (tool.isDrawingTool()) {
                        EditorToolRow(drawingTools, tool, !saving) { tool = it }
                    }
                    if (tool.isDrawingTool()) {
                        EditorColors(color, !saving) { color = it }
                    }
                    if (tool.isDrawingTool() || tool == ImageEditTool.MOSAIC || tool == ImageEditTool.ERASER) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.image_editor_width), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            Slider(value = strokeWidth, onValueChange = { strokeWidth = it }, valueRange = 0.003f..0.04f, enabled = !saving, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        }
                    }
                    EditorToolRow(primaryTools, if (tool.isDrawingTool()) ImageEditTool.PEN else tool, !saving) { tool = it }
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
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { confirmLeave = false; save() }) { Text(stringResource(R.string.save)) }
                        TextButton(onClick = { confirmLeave = false; onDismiss() }) { Text(stringResource(R.string.image_editor_discard)) }
                        TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.image_editor_continue)) }
                    }
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
                    val label = ImageEditMark.Text(text.trim(), target.position, textColor, textSize)
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
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = 24.dp).imePadding(),
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
                Slider(value = textSize, onValueChange = onTextSizeChange, valueRange = 0.02f..0.14f)
                EditorColors(textColor, true, onTextColorChange)
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

@Composable
private fun EditorToolRow(
    tools: List<ImageEditTool>,
    selected: ImageEditTool,
    enabled: Boolean,
    onToolSelected: (ImageEditTool) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tools.forEach { entry ->
            val description = stringResource(editorToolLabel(entry))
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected == entry) Color(0xFF343434) else Color.Transparent)
                    .clickable(enabled = enabled) { onToolSelected(entry) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                EditorToolIcon(entry)
            }
        }
    }
}

@Composable
private fun EditorColors(selected: Int, enabled: Boolean, onColor: (Int) -> Unit) {
    val colors = listOf(0xFFFF3B30, 0xFFFFCC00, 0xFF34C759, 0xFF00BFFF, 0xFFAF52DE, 0xFFFFFFFF, 0xFF000000)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { value ->
            val argb = value.toInt()
            Box(Modifier.size(30.dp).padding(2.dp).clip(CircleShape)
                .background(Color(argb))
                .border(if (selected == argb) 3.dp else 1.dp, if (selected == argb) Color(0xFF8BC7FF) else Color.Gray, CircleShape)
                .clickable(enabled = enabled) { onColor(argb) }
                .then(Modifier),
            )
        }
    }
}

private fun editorToolLabel(tool: ImageEditTool): Int = when (tool) {
    ImageEditTool.MOVE -> R.string.image_editor_move
    ImageEditTool.PEN -> R.string.image_editor_pen
    ImageEditTool.RECTANGLE -> R.string.image_editor_rectangle
    ImageEditTool.OVAL -> R.string.image_editor_oval
    ImageEditTool.ARROW -> R.string.image_editor_arrow
    ImageEditTool.MOSAIC -> R.string.image_editor_mosaic
    ImageEditTool.TEXT -> R.string.image_editor_text
    ImageEditTool.CROP -> R.string.image_editor_crop
    ImageEditTool.ERASER -> R.string.image_editor_eraser
}

private fun ImageEditTool.isDrawingTool(): Boolean = this == ImageEditTool.PEN ||
    this == ImageEditTool.RECTANGLE || this == ImageEditTool.OVAL || this == ImageEditTool.ARROW

@Composable
private fun EditorToolIcon(tool: ImageEditTool) {
    val icon = when (tool) {
        ImageEditTool.PEN -> Icons.Default.Brush
        ImageEditTool.TEXT -> Icons.Default.TextFields
        ImageEditTool.CROP -> Icons.Default.Crop
        else -> null
    }
    if (icon != null) Icon(icon, stringResource(editorToolLabel(tool)), tint = Color.White, modifier = Modifier.size(22.dp))
    else Canvas(Modifier.size(22.dp)) {
        val lineWidth = 2.dp.toPx()
        val start = Offset(size.width * 0.16f, size.height * 0.16f)
        val end = Offset(size.width * 0.84f, size.height * 0.84f)
        when (tool) {
            ImageEditTool.MOVE -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                val arm = size.width * 0.32f
                drawLine(Color.White, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), lineWidth)
                drawLine(Color.White, Offset(center.x - arm, center.y), Offset(center.x - arm + lineWidth * 2f, center.y - lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x - arm, center.y), Offset(center.x - arm + lineWidth * 2f, center.y + lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x + arm, center.y), Offset(center.x + arm - lineWidth * 2f, center.y - lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x + arm, center.y), Offset(center.x + arm - lineWidth * 2f, center.y + lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y - arm), Offset(center.x - lineWidth * 2f, center.y - arm + lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y - arm), Offset(center.x + lineWidth * 2f, center.y - arm + lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y + arm), Offset(center.x - lineWidth * 2f, center.y + arm - lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y + arm), Offset(center.x + lineWidth * 2f, center.y + arm - lineWidth * 2f), lineWidth)
            }
            ImageEditTool.RECTANGLE -> drawRect(Color.White, start, Size(end.x - start.x, end.y - start.y), style = Stroke(lineWidth))
            ImageEditTool.OVAL -> drawOval(Color.White, start, Size(end.x - start.x, end.y - start.y), style = Stroke(lineWidth))
            ImageEditTool.ARROW -> {
                drawLine(Color.White, Offset(start.x, end.y), Offset(end.x, start.y), lineWidth)
                drawLine(Color.White, Offset(end.x, start.y), Offset(end.x, size.height * 0.55f), lineWidth)
                drawLine(Color.White, Offset(end.x, start.y), Offset(size.width * 0.45f, start.y), lineWidth)
            }
            ImageEditTool.MOSAIC -> for (x in 0..2) for (y in 0..2) drawRect(if ((x + y) % 2 == 0) Color.White else Color.Gray, Offset(x * size.width / 3f, y * size.height / 3f), Size(size.width / 3f, size.height / 3f))
            ImageEditTool.ERASER -> {
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(start.x, size.height * 0.6f); lineTo(size.width * 0.6f, start.y); lineTo(end.x, size.height * 0.4f); lineTo(size.width * 0.4f, end.y); close() }
                drawPath(path, Color.White, style = Stroke(lineWidth))
            }
            else -> Unit
        }
    }
}
