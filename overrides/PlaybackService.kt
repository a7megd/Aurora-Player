package com.aurora.player.playback

import android.app.PendingIntent
import android.content.Intent
import com.aurora.player.MainActivity
import android.content.ContentUris
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aurora.player.MusicApp
import com.aurora.player.data.local.PlayHistoryEntity
import com.aurora.player.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            // Keep the CPU awake while local audio is actively playing in the background.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true // Let Media3 manage audio focus, interruptions and resume rules.
                )
                // Pause safely if a Bluetooth or wired output disappears.
                setHandleAudioBecomingNoisy(true)
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                        serviceScope.launch {
                            (application as MusicApp).container.database.historyDao()
                                .insert(PlayHistoryEntity(songId = id, playedAt = System.currentTimeMillis()))
                        }
                    }

                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) persistQueue()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w("WhisperPlayback", "Playback failed for ${player.currentMediaItem?.mediaId}", error)
                        // A damaged or unsupported local file should not strand the whole queue.
                        // Preserve user intent and continue with the next item when one exists.
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            player.prepare()
                            player.playWhenReady = true
                        }
                    }
                })
            }
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this, 1001, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Tapping the system media card opens Whisper Player; transport buttons remain media controls.
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        restoreQueue()
    }

    private fun persistQueue() {
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        serviceScope.launch { (application as MusicApp).container.database.queueDao().replaceAll(ids) }
    }

    private fun restoreQueue() {
        serviceScope.launch {
            val ids = (application as MusicApp).container.database.queueDao().getAll()
            if (ids.isEmpty()) return@launch
            val items = ids.map { id ->
                val uri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                MediaItem.Builder().setMediaId(id.toString()).setUri(uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("Audio $id").build()).build()
            }
            withContext(Dispatchers.Main) { if (player.mediaItemCount == 0) player.setMediaItems(items) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        player.release()
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun mediaItem(song: Song): MediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString()).setUri(Uri.parse(song.contentUri))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                .setAlbumTitle(song.album).setArtworkUri(song.artworkUri?.let(Uri::parse)).build()).build()
    }
}
