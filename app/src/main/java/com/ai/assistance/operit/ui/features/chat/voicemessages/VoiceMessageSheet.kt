package com.ai.assistance.operit.ui.features.chat.voicemessages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R
import kotlinx.coroutines.launch
import com.ai.assistance.operit.core.chat.voice.VoiceMessageData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMessageSheet(onDismiss: () -> Unit, onSend: suspend (VoiceMessageData, () -> Unit) -> Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember { VoiceMessageSession(context.applicationContext) }
    val state by session.state.collectAsState()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) session.start() else session.permissionDenied()
    }
    fun requestRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) session.start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { requestRecording() }
    DisposableEffect(session, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { session.close(); latestDismiss() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); session.close() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (state.phase == VoiceMessagePhase.RECORDING || state.phase == VoiceMessagePhase.PREPARING)
                    R.string.voice_message_record_title else R.string.voice_message_confirm_title),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
            }
            when (state.phase) {
                VoiceMessagePhase.PREPARING -> Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                VoiceMessagePhase.RECORDING -> {
                    Text(voiceDurationLabel(state.elapsedMs), modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.headlineMedium)
                    VoiceWaveform(state.levels, Modifier.fillMaxWidth().height(140.dp))
                    Text(stringResource(R.string.voice_message_silence_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                else -> {
                    state.recording?.let { recording ->
                        VoiceMessagePlayer(recording.file.absolutePath, recording.durationMs, recording.waveform)
                    }
                    if (state.phase == VoiceMessagePhase.TRANSCRIBING) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.voice_message_transcribing))
                        }
                    }
                    state.message?.let { message ->
                        Text(message.transcript, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()))
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (state.phase == VoiceMessagePhase.RECORDING) {
                Button(onClick = session::finish, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.voice_message_done), style = MaterialTheme.typography.titleMedium)
                }
            } else if (state.phase == VoiceMessagePhase.READY || state.phase == VoiceMessagePhase.ERROR) {
                if (state.phase == VoiceMessagePhase.ERROR && state.recording != null) {
                    TextButton(onClick = session::retryTranscription, enabled = !sending) { Text(stringResource(R.string.voice_message_retry)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = ::requestRecording, enabled = !sending, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.voice_message_rerecord))
                    }
                    Button(onClick = {
                        val message = state.message
                        if (message != null && !sending) {
                            sending = true
                            session.beginSend()
                            scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                try {
                                    if (onSend(message, session::retainSentRecording)) onDismiss()
                                } catch (error: kotlinx.coroutines.CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    session.sendFailed(error)
                                } finally {
                                    session.finishSend()
                                    sending = false
                                }
                            }
                        }
                    }, enabled = state.phase == VoiceMessagePhase.READY && !sending, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.send))
                    }
                }
            }
        }
    }
}
