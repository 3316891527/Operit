package com.ai.assistance.operit.ui.features.settings.components.speech

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.ui.features.assistant.components.CompactSwitchRow
import com.ai.assistance.operit.ui.features.assistant.components.VoiceAutoAttachGrid
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import kotlinx.coroutines.launch

/** 语音唤醒设置共用原有配置键，系统默认助手设置由一个按钮打开。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val wakePrefs = remember { WakeWordPreferences(context.applicationContext) }
    val wakeListeningEnabled by wakePrefs.alwaysListeningEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_ALWAYS_LISTENING_ENABLED)
    val wakePhrase by wakePrefs.wakePhraseFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE)
    val wakePhraseRegexEnabled by wakePrefs.wakePhraseRegexEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE_REGEX_ENABLED)
    val wakeRecognitionMode by wakePrefs.wakeRecognitionModeFlow.collectAsState(initial = WakeWordPreferences.WakeRecognitionMode.STT)
    val personalWakeTemplates by wakePrefs.personalWakeTemplatesFlow.collectAsState(initial = emptyList())
    val inactivityTimeoutSeconds by wakePrefs.voiceCallInactivityTimeoutSecondsFlow.collectAsState(
        initial = WakeWordPreferences.DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS
    )
    val wakeGreetingEnabled by wakePrefs.wakeGreetingEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_ENABLED)
    val wakeGreetingText by wakePrefs.wakeGreetingTextFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT)
    val wakeCreateNewChatOnWakeEnabled by wakePrefs.wakeCreateNewChatOnWakeEnabledFlow.collectAsState(
        initial = WakeWordPreferences.DEFAULT_WAKE_CREATE_NEW_CHAT_ON_WAKE_ENABLED
    )
    val autoNewChatGroup by wakePrefs.autoNewChatGroupFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP)
    val voiceAutoAttachEnabled by wakePrefs.voiceAutoAttachEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_VOICE_AUTO_ATTACH_ENABLED)
    val voiceAutoAttachItems by wakePrefs.voiceAutoAttachItemsFlow.collectAsState(initial = WakeWordPreferences.getDefaultVoiceAutoAttachItems(context))
    val coroutineScope = rememberCoroutineScope()

    val requestMicPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                coroutineScope.launch {
                    wakePrefs.saveAlwaysListeningEnabled(true)
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.microphone_permission_denied_toast),
                    android.widget.Toast.LENGTH_SHORT
                )
                    .show()
            }
        }

    var wakePhraseInput by remember { mutableStateOf("") }
    var inactivityTimeoutInput by remember { mutableStateOf("") }
    var wakeGreetingTextInput by remember { mutableStateOf("") }
    var autoNewChatGroupInput by remember { mutableStateOf("") }

    var personalWakeConfigDialogVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(wakePhrase) {
        if (wakePhraseInput.isBlank()) {
            wakePhraseInput = wakePhrase
        }
    }

    LaunchedEffect(inactivityTimeoutSeconds) {
        if (inactivityTimeoutInput.isBlank()) {
            inactivityTimeoutInput = inactivityTimeoutSeconds.toString()
        }
    }

    LaunchedEffect(wakeGreetingText) {
        if (wakeGreetingTextInput.isBlank()) {
            wakeGreetingTextInput = wakeGreetingText
        }
    }

    LaunchedEffect(autoNewChatGroup) {
        if (autoNewChatGroupInput.isBlank()) {
            autoNewChatGroupInput = autoNewChatGroup
        }
    }

    val requestPersonalMicPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            personalWakeConfigDialogVisible = true
        } else {
            android.widget.Toast.makeText(context, context.getString(R.string.microphone_permission_denied_toast), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (error: Exception) {
                    AppLogger.e("WakeSettings", "打开系统助手设置失败", error)
                    Toast.makeText(context, context.getString(R.string.cannot_open_settings), Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.default_assistant_guide_open_settings))
        }
        WakeSettingsCard {
            // 唤醒识别模式
            Text(
                text = stringResource(R.string.voice_wakeup_mode_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            var modeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = modeExpanded,
                onExpandedChange = { modeExpanded = it }
            ) {
                val modeLabel =
                    when (wakeRecognitionMode) {
                        WakeWordPreferences.WakeRecognitionMode.STT -> stringResource(R.string.voice_wakeup_mode_stt)
                        WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE -> stringResource(R.string.voice_wakeup_mode_personal)
                    }
                OutlinedTextField(
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    value = modeLabel,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp),
                )

                ExposedDropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.voice_wakeup_mode_stt)) },
                        onClick = {
                            modeExpanded = false
                            coroutineScope.launch {
                                wakePrefs.saveWakeRecognitionMode(WakeWordPreferences.WakeRecognitionMode.STT)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.voice_wakeup_mode_personal)) },
                        onClick = {
                            modeExpanded = false
                            coroutineScope.launch {
                                wakePrefs.saveWakeRecognitionMode(WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE)
                            }
                        }
                    )
                }
            }

            if (wakeRecognitionMode == WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE) {
                if (personalWakeTemplates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.voice_wakeup_personal_no_templates),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (granted) personalWakeConfigDialogVisible = true
                            else requestPersonalMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    ) {
                        Text(text = stringResource(R.string.voice_wakeup_personal_configure))
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = personalWakeTemplates.isNotEmpty(),
                        onClick = {
                            coroutineScope.launch {
                                wakePrefs.savePersonalWakeTemplates(emptyList())
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.voice_wakeup_personal_clear))
                    }
                }
            }

            // 持续监听与麦克风权限
            CompactSwitchRow(
                title = stringResource(R.string.voice_wakeup_always_listen_title),
                description = stringResource(R.string.voice_wakeup_always_listen_desc),
                checked = wakeListeningEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        val granted =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            coroutineScope.launch {
                                wakePrefs.saveAlwaysListeningEnabled(true)
                            }
                        } else {
                            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        coroutineScope.launch {
                            wakePrefs.saveAlwaysListeningEnabled(false)
                        }
                    }
                }
            )

            if (wakeRecognitionMode == WakeWordPreferences.WakeRecognitionMode.STT) {
                // 唤醒词
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    value = wakePhraseInput,
                    onValueChange = { newValue ->
                        wakePhraseInput = newValue
                        coroutineScope.launch {
                            wakePrefs.saveWakePhrase(newValue.ifBlank { WakeWordPreferences.DEFAULT_WAKE_PHRASE })
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = { Text(stringResource(R.string.voice_wakeup_phrase_label)) },
                    placeholder = { Text(stringResource(R.string.voice_wakeup_phrase_supporting)) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                // 唤醒词正则匹配
                CompactSwitchRow(
                    title = stringResource(R.string.voice_wakeup_regex_title),
                    description = stringResource(R.string.voice_wakeup_regex_desc),
                    checked = wakePhraseRegexEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            wakePrefs.saveWakePhraseRegexEnabled(enabled)
                        }
                    }
                )
            }
        }
        WakeSettingsCard {
            // 通话空闲超时
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                value = inactivityTimeoutInput,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() }
                    inactivityTimeoutInput = filtered
                    val parsed = filtered.toIntOrNull()
                    if (parsed != null) {
                        val clamped = parsed.coerceIn(1, 600)
                        coroutineScope.launch {
                            wakePrefs.saveVoiceCallInactivityTimeoutSeconds(clamped)
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                label = { Text(stringResource(R.string.voice_wakeup_inactivity_timeout_label)) },
                placeholder = { Text(stringResource(R.string.voice_wakeup_inactivity_timeout_supporting)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            // 唤醒问候开关
            CompactSwitchRow(
                title = stringResource(R.string.voice_wakeup_greeting_title),
                description = stringResource(R.string.voice_wakeup_greeting_desc),
                checked = wakeGreetingEnabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        wakePrefs.saveWakeGreetingEnabled(enabled)
                    }
                }
            )

            // 唤醒问候内容
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                value = wakeGreetingTextInput,
                onValueChange = { newValue ->
                    wakeGreetingTextInput = newValue
                    coroutineScope.launch {
                        wakePrefs.saveWakeGreetingText(newValue.ifBlank { WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT })
                    }
                },
                singleLine = true,
                enabled = wakeGreetingEnabled,
                textStyle = MaterialTheme.typography.bodyMedium,
                label = { Text(stringResource(R.string.voice_wakeup_greeting_text_label)) },
                placeholder = { Text(stringResource(R.string.voice_wakeup_greeting_text_supporting)) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            CompactSwitchRow(
                title = stringResource(R.string.voice_wakeup_create_new_chat_title),
                description = stringResource(R.string.voice_wakeup_create_new_chat_desc),
                checked = wakeCreateNewChatOnWakeEnabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        wakePrefs.saveWakeCreateNewChatOnWakeEnabled(enabled)
                    }
                }
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                value = autoNewChatGroupInput,
                onValueChange = { newValue ->
                    autoNewChatGroupInput = newValue
                    coroutineScope.launch {
                        wakePrefs.saveAutoNewChatGroup(
                            newValue.ifBlank { WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP }
                        )
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                label = { Text(stringResource(R.string.voice_wakeup_auto_new_chat_group_label)) },
                placeholder = {
                    Text(
                        stringResource(
                            R.string.voice_wakeup_auto_new_chat_group_supporting,
                            WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP
                        )
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
        WakeSettingsCard {
            Text(
                text = stringResource(R.string.voice_keyword_attachments_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            CompactSwitchRow(
                title = stringResource(R.string.voice_keyword_attachments_enabled_title),
                description = stringResource(R.string.voice_keyword_attachments_enabled_desc),
                checked = voiceAutoAttachEnabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        wakePrefs.saveVoiceAutoAttachEnabled(enabled)
                    }
                }
            )

            if (voiceAutoAttachEnabled) {
                VoiceAutoAttachGrid(
                    items = voiceAutoAttachItems,
                    onItemsChange = { newItems ->
                        coroutineScope.launch {
                            wakePrefs.saveVoiceAutoAttachItems(newItems)
                        }
                    }
                )
            }
        }
    }
    if (personalWakeConfigDialogVisible && isCurrentScreen) {
        PersonalWakeEnrollmentDialog(wakePrefs = wakePrefs, onDismiss = { personalWakeConfigDialogVisible = false })
    }
}

@Composable
private fun WakeSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
