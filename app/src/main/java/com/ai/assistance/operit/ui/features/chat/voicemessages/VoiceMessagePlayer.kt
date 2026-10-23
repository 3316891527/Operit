package com.ai.assistance.operit.ui.features.chat.voicemessages

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private object VoicePlaybackCoordinator {
    var stopCurrent: (() -> Unit)? = null
}

internal fun voiceDurationLabel(milliseconds: Long): String =
    String.format(Locale.ROOT, "%02d:%02d", milliseconds / 60000, milliseconds / 1000 % 60)

@Composable
internal fun VoiceWaveform(levels: List<Float>, modifier: Modifier = Modifier, progress: Float = 0f) {
    val primary = MaterialTheme.colorScheme.primary
    val remaining = primary.copy(alpha = 0.4f)
    val samples = List(maxOf(0, 48 - levels.size)) { 0f } + levels
    Canvas(modifier) {
        val count = samples.size
        val step = size.width / count
        repeat(count) { index ->
            val level = samples[index].coerceIn(0f, 1f)
            val halfHeight = maxOf(1.5.dp.toPx(), level * size.height * 0.46f)
            val x = step * (index + 0.5f)
            drawLine(if (progress <= 0f || index.toFloat() / count <= progress) primary else remaining,
                Offset(x, size.height / 2 - halfHeight), Offset(x, size.height / 2 + halfHeight),
                strokeWidth = minOf(3.dp.toPx(), step * 0.45f), cap = StrokeCap.Round)
        }
    }
}

/** 点击时才创建播放器，历史列表中的未播放语音条不会分配播放资源。 */
@Composable
fun VoiceMessagePlayer(
    filePath: String,
    durationMs: Long,
    waveform: List<Float>,
    transcript: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember(filePath) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember(filePath) { mutableStateOf(false) }
    var preparing by remember(filePath) { mutableStateOf(false) }
    var position by remember(filePath) { mutableStateOf(0L) }
    var error by remember(filePath) { mutableStateOf(false) }
    var showText by remember(filePath) { mutableStateOf(false) }
    val stop = remember(filePath) {
        {
            player?.release()
            player = null
            playing = false
            preparing = false
            position = 0L
        }
    }
    DisposableEffect(lifecycleOwner, filePath) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (VoicePlaybackCoordinator.stopCurrent === stop) VoicePlaybackCoordinator.stopCurrent = null
            stop()
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = player?.currentPosition ?: 0L
            delay(150)
        }
    }
    fun toggle() {
        if (preparing) return
        if (playing) { player?.pause(); return }
        error = false
        if (!File(filePath).isFile) { error = true; return }
        if (player == null) {
            VoicePlaybackCoordinator.stopCurrent?.invoke()
            VoicePlaybackCoordinator.stopCurrent = stop
            preparing = true
            val created = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
                setHandleAudioBecomingNoisy(true)
            }
            player = created
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    preparing = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_ENDED) { playing = false; position = 0 }
                }
                override fun onPlayerError(playbackError: PlaybackException) {
                    AppLogger.e("VoiceMessagePlayer", "播放语音消息失败", playbackError)
                    error = true
                    stop()
                }
            })
            created.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
            created.prepare()
        }
        val active = player ?: return
        if (active.playbackState == Player.STATE_ENDED) active.seekTo(0)
        active.play()
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = ::toggle),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (preparing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (playing) R.string.voice_message_pause else R.string.voice_message_play),
                    tint = MaterialTheme.colorScheme.primary)
                VoiceWaveform(waveform, Modifier.weight(1f).height(36.dp),
                    progress = if (durationMs > 0) position.toFloat() / durationMs else 0f)
                Text(voiceDurationLabel(durationMs), style = MaterialTheme.typography.labelLarge)
            }
            if (error) Text(stringResource(R.string.voice_message_play_failed), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            if (transcript.isNotBlank()) {
                TextButton(onClick = { showText = !showText }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(if (showText) R.string.voice_message_hide_text else R.string.voice_message_show_text))
                }
                if (showText) Text(transcript, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}
