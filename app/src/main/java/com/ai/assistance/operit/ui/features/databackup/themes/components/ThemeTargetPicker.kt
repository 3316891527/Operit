package com.ai.assistance.operit.ui.features.databackup.themes.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.preferences.UserPreferencesManager

/** 角色选择留在当前导入抽屉中，返回选择面板不会取消已校验的主题。 */
@Composable
internal fun ThemeTargetPicker(
    targets: List<CharacterCard>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered =
        remember(targets, query) {
            targets.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ThemeTransferTextField(
            query,
            { query = it },
            label = "",
            placeholder = stringResource(R.string.theme_transfer_search_characters),
            enabled = enabled,
            leadingIcon = Icons.Default.Search,
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            if (filtered.isEmpty())
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.theme_transfer_no_characters),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            items(filtered, key = { it.id }) { card ->
                val selected = card.id == selectedId
                val shape = RoundedCornerShape(16.dp)
                Surface(
                    color =
                        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = shape,
                    border =
                        BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                    modifier =
                        Modifier.fillMaxWidth().clip(shape).selectable(
                            selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                        ) {
                            onSelect(card.id)
                        },
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ThemeCharacterAvatar(card.id)
                        Text(
                            card.name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            Modifier.size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected)
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThemeTargetSelection(card: CharacterCard?, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (card == null) ThemeTransferRoleAvatar(null) else ThemeCharacterAvatar(card.id)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.theme_transfer_apply_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    card?.name ?: stringResource(R.string.theme_transfer_target),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeCharacterAvatar(characterId: String) {
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val avatar by
        remember(preferences, characterId) {
                preferences.getAiAvatarForCharacterCardFlow(characterId)
            }
            .collectAsState(initial = null)
    ThemeTransferRoleAvatar(avatar)
}
