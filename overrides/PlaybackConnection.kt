package com.aurora.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.aurora.player.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

/** A single app-wide bridge to the service-owned player. Commands issued while the
 * controller is still connecting are retained and executed as soon as it is ready. */
class PlaybackConnection(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor: Executor = appContext.mainExecutor
    private val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
    private var future: ListenableFuture<MediaController>? = null
    private var connecting = false
    private var released = false
    private var pendingCommand: ((MediaController) -> Unit)? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryDelayMs = 500L
    private val retryRunnable = Runnable { connect() }
    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (released || _controller.value !== controller) return
            _controller.value = null
            connecting = false
            scheduleReconnect()
        }
    }

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller = _controller.asStateFlow()
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError = _connectionError.asStateFlow()

    init { connect() }

    private fun scheduleReconnect() {
        if (released) return
        retryHandler.removeCallbacks(retryRunnable)
        retryHandler.postDelayed(retryRunnable, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2L).coerceAtMost(8_000L)
    }

    private fun connect() {
        if (released || connecting || _controller.value != null) return
        retryHandler.removeCallbacks(retryRunnable)
        connecting = true
        val attempt = MediaController.Builder(appContext, token)
            .setListener(controllerListener)
            .buildAsync()
        future = attempt
        attempt.addListener({
            connecting = false
            if (released) {
                MediaController.releaseFuture(attempt)
                return@addListener
            }
            try {
                val connected = attempt.get()
                _controller.value = connected
                retryDelayMs = 500L
                _connectionError.value = null
                val command = pendingCommand
                pendingCommand = null
                command?.invoke(connected)
            } catch (error: Exception) {
                _connectionError.value = error.message ?: "تعذر الاتصال بخدمة تشغيل الصوت"
                // Keep the last requested play command and retry with bounded backoff.
                scheduleReconnect()
            }
        }, mainExecutor)
    }

    private fun dispatch(command: (MediaController) -> Unit) {
        val connected = _controller.value
        if (connected != null) {
            command(connected)
        } else {
            // Keep the latest explicit playback request; never silently drop a tap during startup.
            pendingCommand = command
            connect()
        }
    }

    fun retryConnection() = connect()

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val items = songs.map(PlaybackService::mediaItem)
        val index = startIndex.coerceIn(items.indices)
        dispatch { controller ->
            controller.setMediaItems(items, index, 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun play(song: Song, library: List<Song>) {
        val index = library.indexOfFirst { it.id == song.id }
        playQueue(if (index >= 0) library else listOf(song), if (index >= 0) index else 0)
    }

    fun release() {
        released = true
        retryHandler.removeCallbacks(retryRunnable)
        pendingCommand = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _controller.value = null
    }
}
