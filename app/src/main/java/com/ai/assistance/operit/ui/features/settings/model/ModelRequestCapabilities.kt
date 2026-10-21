package com.ai.assistance.operit.ui.features.settings.model

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 只组织能力开关，实际请求语义和持久化由现有连接编辑器维护。 */
@Composable
fun ModelRequestCapabilities(
    streaming: Boolean,
    onStreamingChange: (Boolean) -> Unit,
    toolCall: Boolean,
    onToolCallChange: (Boolean) -> Unit,
    images: Boolean,
    onImagesChange: (Boolean) -> Unit,
    audio: Boolean,
    onAudioChange: (Boolean) -> Unit,
    video: Boolean,
    onVideoChange: (Boolean) -> Unit,
    supportsAudioVideo: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapabilityTile(
                title = stringResource(R.string.model_workspace_streaming),
                hint = stringResource(R.string.model_workspace_global),
                icon = Icons.Default.Speed,
                checked = streaming,
                onCheckedChange = onStreamingChange,
                modifier = Modifier.weight(1f),
            )
            CapabilityTile(
                title = stringResource(R.string.model_workspace_tool_call),
                icon = Icons.Default.Build,
                checked = toolCall,
                onCheckedChange = onToolCallChange,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapabilityTile(
                title = stringResource(R.string.model_workspace_images),
                icon = Icons.Default.Image,
                checked = images,
                onCheckedChange = onImagesChange,
                modifier = Modifier.weight(1f),
            )
            CapabilityTile(
                title = stringResource(R.string.model_workspace_audio),
                icon = Icons.Default.Mic,
                checked = audio && supportsAudioVideo,
                onCheckedChange = onAudioChange,
                enabled = supportsAudioVideo,
                hint = if (supportsAudioVideo) null else stringResource(R.string.model_workspace_media_unavailable),
                modifier = Modifier.weight(1f),
            )
        }
        CapabilityTile(
            title = stringResource(R.string.model_workspace_video),
            icon = Icons.Default.Videocam,
            checked = video && supportsAudioVideo,
            onCheckedChange = onVideoChange,
            enabled = supportsAudioVideo,
            hint = if (supportsAudioVideo) null else stringResource(R.string.model_workspace_media_unavailable),
        )
        Text(stringResource(R.string.model_workspace_streaming_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CapabilityTile(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            }
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
