package com.ai.assistance.operit.ui.features.conversation.chat.webview.workspace.browser.transfer

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.files.transfer.FileCopyPhase

@Composable
internal fun WorkspaceCopyProgressDialog(progress: WorkspaceCopyProgress, cancelling: Boolean, onCancel: () -> Unit) {
    val context = LocalContext.current
    val detail = progress.detail
    val fraction = detail?.fraction?.let { if (progress.moving || cancelling) it.coerceAtMost(0.999f) else it }
    val animatedFraction by animateFloatAsState(fraction ?: 0f, tween(180), label = "workspaceCopyProgress")
    val unknown = "—"
    fun size(bytes: Long): String = Formatter.formatFileSize(context, bytes)
    val currentFile = if (detail == null) progress.source.trimEnd('/').substringAfterLast('/') else detail.currentFile
    val status = when {
        cancelling -> R.string.workspace_transfer_cancelling
        progress.finishing -> R.string.workspace_transfer_finishing
        detail?.phase == FileCopyPhase.SCANNING -> R.string.workspace_transfer_scanning
        detail == null -> R.string.workspace_transfer_processing
        progress.moving -> R.string.workspace_transfer_moving
        else -> R.string.workspace_transfer_copying
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(if (progress.moving) R.string.workspace_transfer_moving else R.string.workspace_transfer_copying),
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (progress.itemCount > 1) {
                    Text(stringResource(R.string.workspace_transfer_item, progress.itemIndex, progress.itemCount),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TransferDetail(stringResource(R.string.workspace_transfer_name), currentFile)
                TransferDetail(stringResource(R.string.workspace_transfer_source), "${progress.sourceEnvironment}:${progress.source}")
                TransferDetail(stringResource(R.string.workspace_transfer_destination), "${progress.destinationEnvironment}:${progress.destination}")
                TransferDetail(stringResource(R.string.workspace_transfer_copied),
                    if (detail == null) unknown else "${size(detail.copiedBytes)} / ${detail.totalBytes?.let(::size) ?: unknown}")
                TransferDetail(stringResource(R.string.workspace_transfer_remaining), detail?.remainingBytes?.let(::size) ?: unknown)
                TransferDetail(stringResource(R.string.workspace_transfer_speed), detail?.bytesPerSecond?.let { "${size(it.toLong())}/s" } ?: unknown)
                if (detail != null) {
                    Text(stringResource(R.string.workspace_transfer_files, detail.copiedFiles, detail.totalFiles),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(status), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (fraction != null) Text(stringResource(R.string.workspace_transfer_percent, (fraction * 100).toInt()),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                val bar = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                if (fraction == null) LinearProgressIndicator(modifier = bar)
                else LinearProgressIndicator(progress = { animatedFraction }, modifier = bar)
                TextButton(onClick = onCancel, enabled = !cancelling, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun TransferDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(0.25f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}