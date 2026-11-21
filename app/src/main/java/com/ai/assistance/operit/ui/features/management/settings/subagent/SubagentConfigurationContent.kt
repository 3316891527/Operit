package com.ai.assistance.operit.ui.features.management.settings.subagent

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.SubagentLimits
import com.ai.assistance.operit.core.agent.SubagentConfiguration
import com.ai.assistance.operit.core.agent.SubagentModel
import com.ai.assistance.operit.core.agent.validate
import com.ai.assistance.operit.core.agent.SubagentPreset
import com.ai.assistance.operit.data.config.model.ModelConfigSelection
import com.ai.assistance.operit.data.config.model.getModelList
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SubagentPreferences
import com.ai.assistance.operit.ui.features.management.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.management.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceDialog
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private data class ConfigurationState(val value: SubagentConfiguration? = null, val failed: Boolean = false)

@Composable
private fun rememberConfiguration(preferences: SubagentPreferences): ConfigurationState {
    return remember(preferences) {
        preferences.configuration.map { ConfigurationState(value = it) }.catch { error ->
            AppLogger.e("SubagentConfiguration", "读取子代理配置失败", error)
            emit(ConfigurationState(failed = true))
        }
    }.collectAsState(initial = ConfigurationState()).value
}

private class ConfigurationWriter(private val scope: CoroutineScope) {
    val pending = mutableListOf<Job>()
    var busy by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var error: Exception? = null
    fun save(action: suspend () -> Unit, done: () -> Unit = {}) {
        if (busy) return
        busy = true
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                action(); failed = false; error = null; done()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure; failed = true
                AppLogger.e("SubagentConfiguration", "保存子代理配置失败", failure)
            } finally { busy = false }
        }
        pending.add(job)
        job.invokeOnCompletion { pending.remove(job) }
        job.start()
    }
}

@Composable
private fun rememberWriter(saveCoordinator: ModelConfigSaveCoordinator, key: String): ConfigurationWriter {
    val scope = rememberCoroutineScope()
    val writer = remember(scope) { ConfigurationWriter(scope) }
    RegisterModelConfigSaveAction(saveCoordinator, key) { _ ->
        writer.pending.toList().joinAll()
        writer.error?.let { throw it }
    }
    return writer
}

@Composable
fun SubagentConfigurationContent(saveCoordinator: ModelConfigSaveCoordinator, onOpenModels: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { SubagentPreferences(context) }
    val loaded = rememberConfiguration(preferences)
    val writer = rememberWriter(saveCoordinator, "subagent-presets")
    var editing by remember { mutableStateOf<SubagentPreset?>(null) }
    var deleting by remember { mutableStateOf<SubagentPreset?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingLimits by remember { mutableStateOf(false) }
    if (loaded.failed) { Text(stringResource(R.string.subagent_config_read_failed)); return }
    val settings = loaded.value
    if (settings == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.subagent_configuration_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onOpenModels, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AccountTree, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.subagent_model_pool_count, settings.models.count { it.enabled }))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.subagent_auto_archive_completed), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.subagent_auto_archive_completed_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = settings.autoArchiveCompleted,
                onCheckedChange = { enabled -> writer.save({ preferences.setAutoArchiveCompleted(enabled) }) },
                enabled = !writer.busy,
            )
        }
        Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.subagent_limits_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.subagent_limits_summary, settings.limits.maxRunning, settings.limits.maxPerChat, settings.limits.executionTimeoutMinutes, settings.limits.maxDepth), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { editingLimits = true }, enabled = !writer.busy) { Text(stringResource(R.string.edit)) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.subagent_prompt_presets), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { adding = true }, enabled = !writer.busy) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text(stringResource(R.string.subagent_add_preset))
            }
        }
        if (settings.presets.isEmpty()) Text(stringResource(R.string.subagent_no_presets), style = MaterialTheme.typography.bodyMedium)
        settings.presets.forEach { preset ->
            Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(preset.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Switch(checked = preset.enabled, onCheckedChange = { enabled -> writer.save({ preferences.savePreset(preset.copy(enabled = enabled)) }) }, enabled = !writer.busy)
                    }
                    Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = { deleting = preset }, enabled = !writer.busy) { Text(stringResource(R.string.delete)) }
                        TextButton(onClick = { editing = preset }, enabled = !writer.busy) { Text(stringResource(R.string.edit)) }
                    }
                }
            }
        }
        if (writer.failed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
    }
    if (editingLimits) LimitsEditor(settings.limits, writer, preferences, saveCoordinator, onDismiss = { editingLimits = false })
    if (adding || editing != null) {
        PresetEditor(editing, writer, preferences, saveCoordinator, onDismiss = { adding = false; editing = null }) { value ->
            writer.save({ preferences.savePreset(value) }) { adding = false; editing = null }
        }
    }
    deleting?.let { preset ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.subagent_delete_preset)) },
            text = { Text(stringResource(R.string.subagent_delete_preset_hint)) },
            confirmButton = { TextButton(onClick = { writer.save({ preferences.deletePreset(preset.id) }) { deleting = null } }, enabled = !writer.busy) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun LimitsEditor(limits: SubagentLimits, writer: ConfigurationWriter, preferences: SubagentPreferences, saveCoordinator: ModelConfigSaveCoordinator, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var global by remember(limits) { mutableStateOf(limits.maxRunning.toString()) }
    var perChat by remember(limits) { mutableStateOf(limits.maxPerChat.toString()) }
    var timeout by remember(limits) { mutableStateOf(limits.executionTimeoutMinutes.toString()) }
    var depth by remember(limits) { mutableStateOf(limits.maxDepth.toString()) }
    var discarding by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = global != limits.maxRunning.toString() || perChat != limits.maxPerChat.toString() || timeout != limits.executionTimeoutMinutes.toString() || depth != limits.maxDepth.toString()
    val globalValue = global.toIntOrNull()
    val chatValue = perChat.toIntOrNull()
    val timeoutValue = timeout.toIntOrNull()
    val globalValid = globalValue != null && globalValue in 1..64
    val chatValid = chatValue != null && globalValue != null && chatValue in 1..globalValue
    val timeoutValid = timeoutValue != null && timeoutValue in 1..120
    val depthValue = depth.toIntOrNull()
    val depthValid = depthValue != null && depthValue in 1..8
    val valid = globalValid && chatValid && timeoutValid && depthValid
    fun value(): SubagentLimits {
        require(valid) { context.getString(R.string.subagent_limits_invalid) }
        return SubagentLimits(requireNotNull(globalValue), requireNotNull(chatValue), requireNotNull(timeoutValue), requireNotNull(depthValue)).validate()
    }
    RegisterModelConfigSaveAction(saveCoordinator, "subagent-limits-draft") { _ ->
        if (!discarding && dirty) preferences.setLimits(value())
    }
    fun discard() { discarding = true; writer.failed = false; writer.error = null; onDismiss() }
    if (confirmDiscard) DiscardDraftDialog(onDismiss = { confirmDiscard = false }, onDiscard = { confirmDiscard = false; discard() })
    ModelWorkspaceDialog(title = stringResource(R.string.subagent_limits_title), onDismiss = { if (!writer.busy) { if (dirty) confirmDiscard = true else discard() } }) {
        Text(stringResource(R.string.subagent_limits_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(global, { if (it.length <= 3 && it.all(Char::isDigit)) global = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.subagent_limits_global)) }, supportingText = { Text(stringResource(R.string.subagent_limits_global_hint)) }, isError = !globalValid, enabled = !writer.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(perChat, { if (it.length <= 3 && it.all(Char::isDigit)) perChat = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.subagent_limits_per_chat)) }, supportingText = { Text(stringResource(R.string.subagent_limits_per_chat_hint)) }, isError = !chatValid, enabled = !writer.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(timeout, { if (it.length <= 3 && it.all(Char::isDigit)) timeout = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.subagent_limits_timeout)) }, supportingText = { Text(stringResource(R.string.subagent_limits_timeout_hint)) }, isError = !timeoutValid, enabled = !writer.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(depth, { if (it.length <= 1 && it.all(Char::isDigit)) depth = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.subagent_limits_depth)) }, supportingText = { Text(stringResource(R.string.subagent_limits_depth_hint)) }, isError = !depthValid, enabled = !writer.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (!valid) Text(stringResource(R.string.subagent_limits_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (writer.failed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { discard() }, enabled = !writer.busy) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { writer.save({ preferences.setLimits(value()) }) { onDismiss() } }, enabled = valid && !writer.busy) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun PresetEditor(preset: SubagentPreset?, writer: ConfigurationWriter, preferences: SubagentPreferences, saveCoordinator: ModelConfigSaveCoordinator, onDismiss: () -> Unit, onSave: (SubagentPreset) -> Unit) {
    val identity = remember(preset?.id) { preset?.id ?: UUID.randomUUID().toString() }
    var name by remember(preset?.id) { mutableStateOf(preset?.name.orEmpty()) }
    var purpose by remember(preset?.id) { mutableStateOf(preset?.description.orEmpty()) }
    var prompt by remember(preset?.id) { mutableStateOf(preset?.systemPrompt.orEmpty()) }
    var discarding by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = name != preset?.name.orEmpty() || purpose != preset?.description.orEmpty() || prompt != preset?.systemPrompt.orEmpty()
    fun value() = SubagentPreset(identity, name.trim(), purpose.trim(), prompt.trim(), preset?.enabled ?: true)
    RegisterModelConfigSaveAction(saveCoordinator, "subagent-preset-draft:$identity") { _ ->
        if (!discarding && dirty) { val draft = value(); draft.validate(); preferences.savePreset(draft) }
    }
    fun discard() { discarding = true; writer.failed = false; writer.error = null; onDismiss() }
    if (confirmDiscard) DiscardDraftDialog(onDismiss = { confirmDiscard = false }, onDiscard = { confirmDiscard = false; discard() })
    ModelWorkspaceDialog(title = stringResource(R.string.subagent_edit_preset), onDismiss = { if (!writer.busy) { if (dirty) confirmDiscard = true else discard() } }) {
        OutlinedTextField(name, { if (it.length <= 60) name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.subagent_preset_name)) })
        OutlinedTextField(purpose, { if (it.length <= 1000) purpose = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.subagent_preset_purpose)) }, supportingText = { Text(stringResource(R.string.subagent_preset_purpose_hint)) })
        OutlinedTextField(prompt, { if (it.length <= 16000) prompt = it }, Modifier.fillMaxWidth(), minLines = 7, label = { Text(stringResource(R.string.subagent_system_prompt)) })
        Text(stringResource(R.string.subagent_preset_independent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (writer.failed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { discard() }, enabled = !writer.busy) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { onSave(value()) }, enabled = !writer.busy && name.isNotBlank() && purpose.isNotBlank() && prompt.isNotBlank()) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
fun SubagentModelPoolContent(saveCoordinator: ModelConfigSaveCoordinator) {
    val context = LocalContext.current
    val preferences = remember { SubagentPreferences(context) }
    val manager = remember { ModelConfigManager(context) }
    val selection by manager.configSelectionFlow.collectAsState(initial = ModelConfigSelection())
    val loaded = rememberConfiguration(preferences)
    val writer = rememberWriter(saveCoordinator, "subagent-model-pool")
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SubagentModel?>(null) }
    if (loaded.failed) { Text(stringResource(R.string.subagent_config_read_failed)); return }
    val settings = loaded.value
    if (settings == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.subagent_model_pool_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        settings.models.forEach { entry ->
            val profile = selection.allConfigs.find { it.id == entry.configId }
            val available = profile != null && entry.modelName in getModelList(profile.modelName)
            Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.modelName, style = MaterialTheme.typography.titleSmall)
                            Text(profile?.name ?: stringResource(R.string.subagent_profile_missing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(entry.enabled, { enabled -> writer.save({ preferences.saveModel(entry.copy(enabled = enabled)) }) }, enabled = !writer.busy && available)
                    }
                    if (!available) Text(stringResource(R.string.subagent_model_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text(entry.capabilities, style = MaterialTheme.typography.bodySmall)
                    if (entry.limitations.isNotBlank()) Text(entry.limitations, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = { writer.save({ preferences.deleteModel(entry.id) }) }, enabled = !writer.busy) { Text(stringResource(R.string.remove)) }
                        TextButton(onClick = { editing = entry }, enabled = !writer.busy && available) { Text(stringResource(R.string.edit)) }
                    }
                }
            }
        }
        if (settings.models.isEmpty()) Text(stringResource(R.string.subagent_no_models), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { adding = true }, enabled = !writer.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.subagent_add_model))
        }
        if (writer.failed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
    }
    if (adding) {
        SubagentModelPicker(selection, settings, onDismiss = { adding = false }) { entry ->
            editing = entry
            adding = false
        }
    }
    editing?.let { entry ->
        ModelCapabilityEditor(entry, writer, preferences, saveCoordinator, onDismiss = { editing = null }) { value ->
            writer.save({ preferences.saveModel(value) }) { editing = null }
        }
    }
}

@Composable
private fun ModelCapabilityEditor(model: SubagentModel, writer: ConfigurationWriter, preferences: SubagentPreferences, saveCoordinator: ModelConfigSaveCoordinator, onDismiss: () -> Unit, onSave: (SubagentModel) -> Unit) {
    var capabilities by remember(model.id) { mutableStateOf(model.capabilities) }
    var limitations by remember(model.id) { mutableStateOf(model.limitations) }
    var discarding by remember(model.id) { mutableStateOf(false) }
    var confirmDiscard by remember(model.id) { mutableStateOf(false) }
    val dirty = capabilities != model.capabilities || limitations != model.limitations
    fun value() = model.copy(capabilities = capabilities.trim(), limitations = limitations.trim())
    RegisterModelConfigSaveAction(saveCoordinator, "subagent-model-draft:${model.id}") { _ ->
        if (!discarding && dirty) { val draft = value(); draft.validate(); preferences.saveModel(draft) }
    }
    fun discard() { discarding = true; writer.failed = false; writer.error = null; onDismiss() }
    if (confirmDiscard) DiscardDraftDialog(onDismiss = { confirmDiscard = false }, onDiscard = { confirmDiscard = false; discard() })
    ModelWorkspaceDialog(title = model.modelName, onDismiss = { if (!writer.busy) { if (dirty) confirmDiscard = true else discard() } }) {
        Text(stringResource(R.string.subagent_capability_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(capabilities, { if (it.length <= 2000) capabilities = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text(stringResource(R.string.subagent_model_capabilities)) }, placeholder = { Text(stringResource(R.string.subagent_model_capabilities_example)) })
        OutlinedTextField(limitations, { if (it.length <= 2000) limitations = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.subagent_model_limitations)) })
        if (writer.failed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { discard() }, enabled = !writer.busy) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { onSave(value()) }, enabled = !writer.busy && capabilities.isNotBlank()) { Text(stringResource(R.string.save)) }
        }
    }
}
@Composable
private fun DiscardDraftDialog(onDismiss: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.subagent_discard_draft)) },
        text = { Text(stringResource(R.string.subagent_discard_draft_hint)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.subagent_discard_draft)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
