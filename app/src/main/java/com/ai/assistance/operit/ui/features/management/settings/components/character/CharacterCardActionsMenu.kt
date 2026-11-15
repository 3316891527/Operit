package com.ai.assistance.operit.ui.features.management.settings.components.character

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 仅负责角色卡按钮弹出的菜单，原有操作条件和确认流程由调用方保留。 */
@Composable
fun CharacterCardActionsMenu(
    expanded: Boolean,
    isActive: Boolean,
    isDefault: Boolean,
    onDismiss: () -> Unit,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    CharacterCardDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        fun invoke(action: () -> Unit) {
            onDismiss()
            action()
        }
        if (!isActive) {
            CharacterCardMenuItem(
                Icons.Default.Check,
                stringResource(R.string.set_active),
                onClick = { invoke(onSetActive) },
            )
            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
        CharacterCardMenuItem(
            Icons.Default.Edit,
            stringResource(R.string.edit),
            onClick = { invoke(onEdit) },
        )
        CharacterCardMenuItem(
            Icons.Default.ContentCopy,
            stringResource(R.string.duplicate),
            onClick = { invoke(onDuplicate) },
        )
        CharacterCardMenuItem(
            Icons.Default.Share,
            stringResource(R.string.export),
            onClick = { invoke(onExport) },
        )
        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        if (isDefault)
            CharacterCardMenuItem(
                Icons.Default.Restore,
                stringResource(R.string.reset),
                destructive = true,
                onClick = { invoke(onReset) },
            )
        else
            CharacterCardMenuItem(
                Icons.Outlined.DeleteOutline,
                stringResource(R.string.delete),
                destructive = true,
                onClick = { invoke(onDelete) },
            )
    }
}
