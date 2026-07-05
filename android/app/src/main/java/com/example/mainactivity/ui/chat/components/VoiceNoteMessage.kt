@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.chat.components

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun VoiceNoteMessage(
    url: String,
    isMine: Boolean,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(url) {
        onDispose {
            player.value?.release()
            player.value = null
        }
    }

    // Pre-fetch duration so the timestamp is correct before the user taps play.
    LaunchedEffect(url) {
        if (url.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(url, emptyMap())
                    val ms =
                        retriever
                            .extractMetadata(
                                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
                            )?.toIntOrNull() ?: 0
                    retriever.release()
                    durationMs = ms
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                val p = player.value
                if (p != null) {
                    if (p.isPlaying) {
                        positionMs = p.currentPosition
                        durationMs = p.duration.takeIf { it > 0 } ?: durationMs
                        progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    } else {
                        isPlaying = false
                        positionMs = 0
                        progress = 0f
                    }
                }
                delay(200)
            }
        }
    }

    fun togglePlay() {
        if (isPlaying) {
            player.value?.pause()
            isPlaying = false
        } else {
            if (player.value == null) {
                val mp = MediaPlayer()
                mp.setDataSource(url)
                mp.prepareAsync()
                mp.setOnPreparedListener { prepared ->
                    durationMs = prepared.duration
                    prepared.start()
                    isPlaying = true
                }
                mp.setOnCompletionListener {
                    isPlaying = false
                    positionMs = 0
                    progress = 0f
                }
                player.value = mp
            } else {
                player.value?.start()
                isPlaying = true
            }
        }
    }

    val contentColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val trackColor =
        if (isMine) {
            Color.White.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        }
    val indicatorColor = if (isMine) Color.White else MaterialTheme.colorScheme.primary

    Row(
        modifier =
            Modifier
                .widthIn(min = 180.dp, max = 240.dp)
                .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = ::togglePlay, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = contentColor,
            )
        }
        Spacer(Modifier.width(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .weight(1f)
                    .height(3.dp),
            color = indicatorColor,
            trackColor = trackColor,
        )
        Spacer(Modifier.width(8.dp))
        val displayMs = if (isPlaying) positionMs else durationMs
        Text(
            text = "%d:%02d".format(displayMs / 60_000, (displayMs % 60_000) / 1000),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}
