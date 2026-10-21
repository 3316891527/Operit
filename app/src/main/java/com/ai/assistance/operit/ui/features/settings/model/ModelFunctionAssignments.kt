package com.ai.assistance.operit.ui.features.settings.model

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSelection
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** 网格只负责浏览，绑定与测试继续使用原功能编辑器，避免改动十二类功能的行为。 */
@Composable
fun ModelFunctionAssignments(currentConfigId: String, saveCoordinator: ModelConfigSaveCoordinator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val functionalManager = remember { FunctionalConfigManager(context) }
    val modelManager = remember { ModelConfigManager(context) }
    val mappings by functionalManager.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())
    val selection by modelManager.configSelectionFlow.collectAsState(initial = ModelConfigSelection())
    var onlyCurrent by remember { mutableStateOf(false) }
    var editingFunction by remember { mutableStateOf<FunctionType?>(null) }
    var showReset by remember { mutableStateOf(false) }
    val pendingUpdates = remember { mutableListOf<Job>() }
    var saveFailure by remember { mutableStateOf<Exception?>(null) }
    RegisterModelConfigSaveAction(saveCoordinator, "function-assignments") { _ ->
        pendingUpdates.toList().joinAll()
        saveFailure?.let { throw it }
    }
    fun updateAssignment(action: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                action()
                saveFailure = null
                OKToast.success(context, R.string.config_saved, OKToast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveFailure = e
                AppLogger.e("ModelFunctionAssignments", "保存功能分配失败", e)
                OKToast.error(context, R.string.save_failed, OKToast.LENGTH_SHORT).show()
            }
        }
        pendingUpdates.add(job)
        job.invokeOnCompletion { pendingUpdates.remove(job) }
        job.start()
    }
    val currentSelection = selection
    val visibleFunctions = FunctionType.values().filter { function ->
        !onlyCurrent || mappings[function]?.configId == currentConfigId
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.model_workspace_function_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !onlyCurrent, onClick = { onlyCurrent = false }, label = { Text(stringResource(R.string.model_workspace_all_functions)) })
            FilterChip(selected = onlyCurrent, onClick = { onlyCurrent = true }, label = { Text(stringResource(R.string.model_workspace_current_functions)) })
        }
        visibleFunctions.chunked(2).forEach { functions ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                functions.forEach { function ->
                    val mapping = mappings[function]
                        ?: FunctionConfigMapping(if (function == FunctionType.MEMORY_EMBEDDING) "" else FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)
                    val config = currentSelection.allConfigs.find { it.id == mapping.configId }
                    Surface(
                        modifier = Modifier.weight(1f).clickable { editingFunction = function },
                        shape = RoundedCornerShape(16.dp),
                        color = if (config?.id == currentConfigId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(functionIcon(function), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Text(getFunctionDisplayName(function), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(config?.name ?: stringResource(R.string.not_configured), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                config?.let { getModelByIndex(it.modelName, getValidModelIndex(it.modelName, mapping.modelIndex)) }.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (functions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (visibleFunctions.isEmpty()) Text(stringResource(R.string.model_workspace_no_functions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { showReset = true }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.reset_all_functions_to_default))
        }
    }
    editingFunction?.let { function ->
        val mapping = mappings[function]
            ?: FunctionConfigMapping(if (function == FunctionType.MEMORY_EMBEDDING) "" else FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)
        val config = currentSelection.allConfigs.find { it.id == mapping.configId }
        val groupName = currentSelection.selectedGroup?.name ?: stringResource(R.string.ungrouped)
        val boundGroup = if (config != null && config.groupId != currentSelection.selectedGroupId) {
            currentSelection.groups.find { it.id == config.groupId }?.name ?: stringResource(R.string.ungrouped)
        } else null
        ModelWorkspaceDialog(title = getFunctionDisplayName(function), onDismiss = { editingFunction = null }) {
            ModelFunctionEditor(
                functionType = function,
                currentConfig = config,
                currentModelIndex = mapping.modelIndex,
                currentMapping = mapping,
                availableConfigs = currentSelection.availableConfigs,
                selectedGroupName = groupName,
                boundConfigGroupName = boundGroup,
                onConfigSelected = { configId, modelIndex ->
                    updateAssignment {
                        functionalManager.setConfigForFunction(function, configId, modelIndex)
                        if (function != FunctionType.MEMORY_EMBEDDING) EnhancedAIService.refreshServiceForFunction(context, function)
                    }
                },
                onThinkingChanged = { enabled, option ->
                    updateAssignment {
                        functionalManager.setThinkingForFunction(function, enabled, option)
                    }
                },
            )
        }
    }
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.reset_all_functions_to_default)) },
            text = { Text(stringResource(R.string.model_workspace_reset_functions_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    updateAssignment {
                        functionalManager.resetAllFunctionConfigs()
                        EnhancedAIService.refreshAllServices(context)
                        showReset = false
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun functionIcon(function: FunctionType) = when (function) {
    FunctionType.CHAT -> Icons.Default.ChatBubbleOutline
    FunctionType.SUMMARY -> Icons.Default.Summarize
    FunctionType.TITLE_GENERATION -> Icons.Default.Title
    FunctionType.MEMORY -> Icons.Default.Memory
    FunctionType.MEMORY_EMBEDDING -> Icons.Default.Storage
    FunctionType.UI_CONTROLLER -> Icons.Default.TouchApp
    FunctionType.TRANSLATION -> Icons.Default.Translate
    FunctionType.GREP -> Icons.Default.Search
    FunctionType.ROLE_RESPONSE_PLANNER -> Icons.Default.People
    FunctionType.IMAGE_RECOGNITION -> Icons.Default.Image
    FunctionType.AUDIO_RECOGNITION -> Icons.Default.Mic
    FunctionType.VIDEO_RECOGNITION -> Icons.Default.Videocam
}