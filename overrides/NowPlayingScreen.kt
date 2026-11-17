package com.aurora.player.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(controller: MediaController?) {
    var position by remember(controller) { mutableLongStateOf(0L) }
    var duration by remember(controller) { mutableLongStateOf(0L) }
    var title by remember(controller) { mutableStateOf("") }
    var artist by remember(controller) { mutableStateOf("") }
    var artwork by remember(controller) { mutableStateOf<android.net.Uri?>(null) }
    var playing by remember(controller) { mutableStateOf(false) }
    var shuffle by remember(controller) { mutableStateOf(false) }
    var repeat by remember(controller) { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var speed by remember(controller) { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let { player ->
                if (!dragging) position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it > 0L } ?: 0L
                val metadata = player.currentMediaItem?.mediaMetadata
                title = metadata?.title?.toString().orEmpty()
                artist = metadata?.artist?.toString().orEmpty()
                artwork = metadata?.artworkUri
                playing = player.isPlaying
                shuffle = player.shuffleModeEnabled
                repeat = player.repeatMode
                speed = player.playbackParameters.speed
            }
            delay(400)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("NOW PLAYING", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        AsyncImage(
            model = artwork,
            contentDescription = "Album artwork",
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            title.ifBlank { "Nothing is playing" },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            artist.ifBlank { "Choose a song from your library" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(28.dp))
        Slider(
            value = if (duration > 0L) position.coerceIn(0L, duration).toFloat() else 0f,
            onValueChange = { dragging = true; position = it.toLong() },
            onValueChangeFinished = { controller?.seekTo(position); dragging = false },
            valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
            enabled = controller != null && duration > 0L
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
            Text("-${formatTime((duration - position).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller?.shuffleModeEnabled = !shuffle }, enabled = controller != null) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = controller != null) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp))
            }
            FilledIconButton(
                onClick = { if (playing) controller?.pause() else controller?.play() },
                enabled = controller != null,
                modifier = Modifier.size(68.dp)
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause", modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = controller != null) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp))
            }
            IconButton(
                onClick = {
                    val next = when (repeat) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    controller?.repeatMode = next
                },
                enabled = controller != null
            ) {
                Icon(
                    if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                TextButton(onClick = {
                    controller?.setPlaybackParameters(PlaybackParameters(rate))
                }, enabled = controller != null) {
                    Text(if (rate == 1f) "1×" else "${rate}×", color = if (speed == rate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
