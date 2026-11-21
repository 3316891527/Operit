package com.ai.assistance.operit.ui.features.management.settings.subagent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.SubagentConfiguration
import com.ai.assistance.operit.core.agent.SubagentModel
import com.ai.assistance.operit.data.config.model.ModelConfigSelection
import com.ai.assistance.operit.data.config.model.getModelList
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceDialog
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField

/** 当前配置组决定添加范围，已有全局模型池不会因切换配置组而丢失。 */
@Composable
internal fun SubagentModelPicker(
    selection: ModelConfigSelection,
    settings: SubagentConfiguration,
    onDismiss: () -> Unit,
    onSelect: (SubagentModel) -> Unit,
) {
    var expandedConfigId by remember(selection.selectedGroupId) { mutableStateOf<String?>(null) }
    var query by remember(selection.selectedGroupId) { mutableStateOf("") }
    val profiles = selection.availableConfigs.filter { profile ->
        profile.name.contains(query, true) || getModelList(profile.modelName).any { it.contains(query, true) }
    }
    ModelWorkspaceDialog(
        title = stringResource(R.string.subagent_choose_models),
        onDismiss = onDismiss,
        scrollable = false,
    ) {
        Text(
            stringResource(R.string.model_config_current_group, selection.selectedGroup?.name ?: stringResource(R.string.ungrouped)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.subagent_models_search)) },
        )
        if (profiles.isEmpty()) {
            Text(stringResource(R.string.subagent_no_models_in_group), style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            profiles.forEach { profile ->
                val models = getModelList(profile.modelName).distinct()
                val expanded = expandedConfigId == profile.id || query.isNotBlank()
                val selectedCount = settings.models.count { it.configId == profile.id && it.modelName in models }
                item(key = "profile:${profile.id}") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                expandedConfigId = if (expandedConfigId == profile.id) null else profile.id
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.subagent_models_selected_count, selectedCount, models.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                stringResource(if (expanded) R.string.model_config_collapse else R.string.model_config_expand),
                            )
                        }
                    }
                }
                if (expanded) {
                    val matching = if (profile.name.contains(query, true)) models else models.filter { it.contains(query, true) }
                    // 型号逐项惰性布局，展开大型配置也不会一次测量整个型号列表。
                    items(matching, key = { "model:${profile.id}:$it" }) { modelName ->
                        val existing = settings.models.find { it.configId == profile.id && it.modelName == modelName }
                        Surface(
                            Modifier.padding(start = 10.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    onSelect(existing ?: SubagentModel(configId = profile.id, modelName = modelName, capabilities = ""))
                                }.padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = existing != null, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(modelName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
