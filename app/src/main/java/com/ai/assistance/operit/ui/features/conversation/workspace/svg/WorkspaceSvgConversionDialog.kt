package com.ai.assistance.operit.ui.features.conversation.workspace.svg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.icons.ImageAssetLoader
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.WorkspaceArchivePicker
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.isValidWorkspaceEntryName
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class WorkspaceSvgConversionRequest(
    val name: String,
    val format: WorkspaceSvgFormat,
    val destination: WorkspaceLocation,
    val readSource: suspend () -> File,
)
private data class SvgExportSource(val bytes: ByteArray, val width: Float, val height: Float)

@Composable
internal fun WorkspaceSvgConversionDialog(request: WorkspaceSvgConversionRequest, onDismiss: () -> Unit,
    onComplete: (WorkspaceLocation) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember(request) { mutableStateOf<SvgExportSource?>(null) }
    var loading by remember(request) { mutableStateOf(true) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var name by remember(request) { mutableStateOf(request.name.substringBeforeLast('.')) }
    var destination by remember(request) { mutableStateOf(request.destination) }
    var width by remember(request) { mutableStateOf("") }
    var height by remember(request) { mutableStateOf("") }
    var keepRatio by remember(request) { mutableStateOf(true) }
    var picker by remember(request) { mutableStateOf(false) }
    var busy by remember(request) { mutableStateOf(false) }
    var job by remember(request) { mutableStateOf<Job?>(null) }
    val png = request.format == WorkspaceSvgFormat.PNG
    val validSize = validSvgExportSize(width.toIntOrNull(), height.toIntOrNull())
    LaunchedEffect(request) {
        try {
            val prepared = withContext(Dispatchers.IO) {
                val file = request.readSource()
                val bytes = file.readBytes()
                val image = bytes.inputStream().use(ImageAssetLoader::readSvg)
                SvgExportSource(bytes, image.width, image.height)
            }
            source = prepared
            width = prepared.width.roundToInt().coerceAtLeast(1).toString()
            height = prepared.height.roundToInt().coerceAtLeast(1).toString()
        } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
            AppLogger.e("WorkspaceSvg", "读取转换源失败", failure)
            error = context.getString(R.string.workspace_svg_error, failure.toString())
        } finally { loading = false }
    }
    fun dismiss() { job?.cancel(); onDismiss() }
    AlertDialog(onDismissRequest = ::dismiss,
        title = { Text(stringResource(if (png) R.string.workspace_svg_to_png else R.string.workspace_svg_to_xml)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(request.name, style = MaterialTheme.typography.bodyMedium)
                if (loading || busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(stringResource(if (busy) R.string.workspace_svg_converting else R.string.loading))
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                    label = { Text(stringResource(R.string.file_manager_file_name)) }, suffix = { Text(".${request.format.extension}") },
                    isError = !isValidWorkspaceEntryName(name))
                Text(stringResource(R.string.workspace_archive_destination), style = MaterialTheme.typography.labelLarge)
                Text("${destination.environment}: ${destination.path}", Modifier.fillMaxWidth().clickable(enabled = !busy) { picker = true }.padding(vertical = 4.dp))
                if (png) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(width, { next ->
                            width = next
                            val data = source
                            val pixels = next.toIntOrNull()
                            if (keepRatio && data != null && pixels != null && pixels in 1..4096) {
                                height = (pixels * data.height / data.width).roundToInt().coerceAtLeast(1).toString()
                            }
                        }, Modifier.weight(1f), singleLine = true, enabled = !busy && !loading,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text(stringResource(R.string.workspace_svg_width)) }, isError = !loading && !validSize)
                        OutlinedTextField(height, { next ->
                            height = next
                            val data = source
                            val pixels = next.toIntOrNull()
                            if (keepRatio && data != null && pixels != null && pixels in 1..4096) {
                                width = (pixels * data.width / data.height).roundToInt().coerceAtLeast(1).toString()
                            }
                        }, Modifier.weight(1f), singleLine = true, enabled = !busy && !loading,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text(stringResource(R.string.workspace_svg_height)) }, isError = !loading && !validSize)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(keepRatio, { keepRatio = it }, enabled = !busy)
                        Text(stringResource(R.string.workspace_svg_keep_ratio))
                    }
                    Text(stringResource(R.string.workspace_svg_size_hint), style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val data = checkNotNull(source)
                val outputWidth = if (png) checkNotNull(width.toIntOrNull()) else 1
                val outputHeight = if (png) checkNotNull(height.toIntOrNull()) else 1
                busy = true
                error = null
                job = scope.launch {
                    try {
                        val output = WorkspaceSvgExport.export(context, data.bytes, request.format, name, destination, outputWidth, outputHeight)
                        onComplete(output)
                    } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
                        AppLogger.e("WorkspaceSvg", "SVG 转换失败", failure)
                        error = if (failure is UnsupportedSvgFeature) context.getString(R.string.workspace_svg_unsupported, failure.feature)
                            else context.getString(R.string.workspace_svg_error, failure.toString())
                    } finally { busy = false; job = null }
                }
            }, enabled = !busy && !loading && source != null && isValidWorkspaceEntryName(name) && (!png || validSize)) {
                Text(stringResource(R.string.workspace_svg_convert))
            }
        },
        dismissButton = { TextButton(onClick = ::dismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picker) WorkspaceArchivePicker(destination, stringResource(R.string.workspace_archive_destination),
        onDismiss = { picker = false }) { location, _ -> destination = location; picker = false }
}
